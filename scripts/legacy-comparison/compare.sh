#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Legacy (Python) vs Kotlin (Spring Boot) API Comparison Script
#
# Compares all GET and write endpoints between the two servers, classifies
# differences as BREAKING / BEHAVIORAL / COSMETIC, and generates a markdown
# report at docs/legacy-comparison-report.md.
#
# Requirements: curl, jq (both assumed available)
###############################################################################

# ── Resolve project root ─────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPORT_FILE="${PROJECT_ROOT}/docs/legacy-comparison-report.md"

# ── Server URLs ──────────────────────────────────────────────────────────────
HOST="ec2-43-203-248-188.ap-northeast-2.compute.amazonaws.com"
LEGACY_BASE="http://${HOST}"
KOTLIN_BASE="http://${HOST}:8080"
LEGACY_API="${LEGACY_BASE}/api/v1"
KOTLIN_API="${KOTLIN_BASE}/api/v1"

# ── Test account ─────────────────────────────────────────────────────────────
TEST_EMAIL="test_compare@example.com"
TEST_PASSWORD="compare1234"
TEST_FCM="fcm-compare-test"

# ── Temp directory & cleanup ─────────────────────────────────────────────────
TMPDIR_COMP="$(mktemp -d)"
trap 'rm -rf "${TMPDIR_COMP}"' EXIT

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ── Counters ─────────────────────────────────────────────────────────────────
TOTAL=0
MATCH=0
BREAKING=0
BEHAVIORAL=0
COSMETIC=0
LAST_LEGACY_STATUS=""
LAST_LEGACY_BODY=""
LAST_KOTLIN_STATUS=""
LAST_KOTLIN_BODY=""

# ── Report accumulators ──────────────────────────────────────────────────────
declare -a MODULE_RESULTS=()   # "module|endpoint|result|details"
declare -a DIFF_SECTIONS=()    # markdown diff blocks

###############################################################################
# Helpers
###############################################################################

log_info()  { echo -e "${CYAN}[INFO]${RESET}  $*"; }
log_ok()    { echo -e "${GREEN}[MATCH]${RESET} $*"; }
log_warn()  { echo -e "${YELLOW}[DIFF]${RESET}  $*"; }
log_err()   { echo -e "${RED}[BREAK]${RESET} $*"; }
log_step()  { echo -e "\n${BOLD}═══ $* ═══${RESET}"; }

# curl wrapper — returns "HTTP_STATUS\nBODY" to a file
# Usage: api_call <method> <url> [extra-curl-args...]
# Writes to $TMPDIR_COMP/response
api_call() {
    local method="$1"; shift
    local url="$1"; shift
    local http_code body

    http_code=$(curl -s -o "${TMPDIR_COMP}/body" -w "%{http_code}" \
        --connect-timeout 10 --max-time 30 \
        -X "${method}" \
        -H "Content-Type: application/json" \
        "$@" \
        "${url}" 2>/dev/null || echo "000")

    body=$(cat "${TMPDIR_COMP}/body" 2>/dev/null || echo "")

    # Ensure body is valid JSON; if not, wrap it
    if ! echo "${body}" | jq empty 2>/dev/null; then
        body="{\"raw\": $(echo "${body}" | jq -Rs .)}"
    fi

    echo "${http_code}"
    echo "${body}"
}

# Compare two API responses.
# Usage: compare_endpoint <module> <label> <method> <path> \
#            [--legacy-args "..."] [--kotlin-args "..."] \
#            [--legacy-url <full_url>] [--kotlin-url <full_url>] \
#            [--body <json>] [--no-auth]
compare_endpoint() {
    local module="$1"; shift
    local label="$1"; shift
    local method="$1"; shift
    local path="$1"; shift

    local legacy_url="${LEGACY_API}${path}"
    local kotlin_url="${KOTLIN_API}${path}"
    local body=""
    local no_auth=false
    local legacy_extra_args=()
    local kotlin_extra_args=()

    # Parse optional flags
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --legacy-url) legacy_url="$2"; shift 2 ;;
            --kotlin-url) kotlin_url="$2"; shift 2 ;;
            --body)       body="$2"; shift 2 ;;
            --no-auth)    no_auth=true; shift ;;
            --legacy-args)
                # Split extra args for legacy
                IFS=' ' read -r -a legacy_extra_args <<< "$2"; shift 2 ;;
            --kotlin-args)
                IFS=' ' read -r -a kotlin_extra_args <<< "$2"; shift 2 ;;
            *) shift ;;
        esac
    done

    local auth_args_legacy=()
    local auth_args_kotlin=()
    if [[ "${no_auth}" == "false" ]]; then
        auth_args_legacy=(-H "Authorization: Bearer ${LEGACY_TOKEN}")
        auth_args_kotlin=(-H "Authorization: Bearer ${KOTLIN_TOKEN}")
    fi

    local body_args=()
    if [[ -n "${body}" ]]; then
        body_args=(-d "${body}")
    fi

    # Call legacy
    local legacy_raw kotlin_raw
    legacy_raw=$(api_call "${method}" "${legacy_url}" \
        "${auth_args_legacy[@]+"${auth_args_legacy[@]}"}" \
        "${body_args[@]+"${body_args[@]}"}" \
        "${legacy_extra_args[@]+"${legacy_extra_args[@]}"}")

    # Call kotlin
    kotlin_raw=$(api_call "${method}" "${kotlin_url}" \
        "${auth_args_kotlin[@]+"${auth_args_kotlin[@]}"}" \
        "${body_args[@]+"${body_args[@]}"}" \
        "${kotlin_extra_args[@]+"${kotlin_extra_args[@]}"}")

    local legacy_status kotlin_status legacy_body kotlin_body
    legacy_status=$(head -1 <<< "${legacy_raw}")
    legacy_body=$(tail -n +2 <<< "${legacy_raw}")
    kotlin_status=$(head -1 <<< "${kotlin_raw}")
    kotlin_body=$(tail -n +2 <<< "${kotlin_raw}")

    # Save for callers that need IDs from responses
    LAST_LEGACY_STATUS="${legacy_status}"
    LAST_LEGACY_BODY="${legacy_body}"
    LAST_KOTLIN_STATUS="${kotlin_status}"
    LAST_KOTLIN_BODY="${kotlin_body}"

    classify_diff "${module}" "${label}" \
        "${legacy_status}" "${legacy_body}" \
        "${kotlin_status}" "${kotlin_body}"
}

# Classify differences between two responses
classify_diff() {
    local module="$1"
    local label="$2"
    local l_status="$3"
    local l_body="$4"
    local k_status="$5"
    local k_body="$6"

    TOTAL=$((TOTAL + 1))

    local l_resp_code l_resp_msg l_data k_resp_code k_resp_msg k_data
    l_resp_code=$(echo "${l_body}" | jq -r '.resp_code // empty' 2>/dev/null || echo "")
    l_resp_msg=$(echo "${l_body}" | jq -r '.resp_msg // empty' 2>/dev/null || echo "")
    l_data=$(echo "${l_body}" | jq -c '.data // .errors // null' 2>/dev/null || echo "null")
    k_resp_code=$(echo "${k_body}" | jq -r '.resp_code // empty' 2>/dev/null || echo "")
    k_resp_msg=$(echo "${k_body}" | jq -r '.resp_msg // empty' 2>/dev/null || echo "")
    k_data=$(echo "${k_body}" | jq -c '.data // .errors // null' 2>/dev/null || echo "null")

    # Extract structural keys (recursively, type-annotated)
    local l_struct k_struct
    l_struct=$(echo "${l_data}" | jq -cS 'def type_tree: if type == "object" then to_entries | map({key: .key, value: (.value | type_tree)}) | from_entries elif type == "array" then if length > 0 then [first | type_tree] else ["empty"] end else type end; type_tree' 2>/dev/null || echo "null")
    k_struct=$(echo "${k_data}" | jq -cS 'def type_tree: if type == "object" then to_entries | map({key: .key, value: (.value | type_tree)}) | from_entries elif type == "array" then if length > 0 then [first | type_tree] else ["empty"] end else type end; type_tree' 2>/dev/null || echo "null")

    local severity="MATCH"
    local details=""

    # Check HTTP status
    if [[ "${l_status}" != "${k_status}" ]]; then
        severity="BREAKING"
        details="HTTP status: legacy=${l_status} kotlin=${k_status}"
    fi

    # Check resp_code
    if [[ "${l_resp_code}" != "${k_resp_code}" ]]; then
        severity="BREAKING"
        details="${details:+${details}; }resp_code: legacy=${l_resp_code} kotlin=${k_resp_code}"
    fi

    # Check data structure
    if [[ "${l_struct}" != "${k_struct}" ]]; then
        # Check if it's just extra fields (cosmetic) or missing fields (breaking)
        local l_keys k_keys
        l_keys=$(echo "${l_data}" | jq -cS '[paths(scalars)] | sort' 2>/dev/null || echo "[]")
        k_keys=$(echo "${k_data}" | jq -cS '[paths(scalars)] | sort' 2>/dev/null || echo "[]")

        if [[ "${severity}" != "BREAKING" ]]; then
            # If Kotlin has all legacy keys, extra keys are cosmetic
            local missing_in_kotlin
            missing_in_kotlin=$(jq -n --argjson l "${l_keys}" --argjson k "${k_keys}" '$l - $k | length')
            if [[ "${missing_in_kotlin}" -gt 0 ]]; then
                severity="BREAKING"
                details="${details:+${details}; }data structure mismatch (keys missing in kotlin)"
            else
                if [[ "${severity}" == "MATCH" ]]; then
                    severity="COSMETIC"
                    details="${details:+${details}; }extra fields in kotlin response"
                fi
            fi
        else
            details="${details:+${details}; }data structure also differs"
        fi
    fi

    # Check resp_msg (only escalate to BEHAVIORAL if not already breaking)
    if [[ "${l_resp_msg}" != "${k_resp_msg}" ]]; then
        if [[ "${severity}" == "MATCH" ]]; then
            severity="BEHAVIORAL"
        fi
        details="${details:+${details}; }resp_msg: legacy='${l_resp_msg}' kotlin='${k_resp_msg}'"
    fi

    # Record result
    case "${severity}" in
        MATCH)
            MATCH=$((MATCH + 1))
            log_ok "${module} > ${label}"
            ;;
        COSMETIC)
            COSMETIC=$((COSMETIC + 1))
            log_warn "${module} > ${label} [COSMETIC] ${details}"
            ;;
        BEHAVIORAL)
            BEHAVIORAL=$((BEHAVIORAL + 1))
            log_warn "${module} > ${label} [BEHAVIORAL] ${details}"
            ;;
        BREAKING)
            BREAKING=$((BREAKING + 1))
            log_err "${module} > ${label} [BREAKING] ${details}"
            ;;
    esac

    MODULE_RESULTS+=("${module}|${label}|${severity}|${details}")

    # Build diff section for non-matches
    if [[ "${severity}" != "MATCH" ]]; then
        local diff_block
        diff_block=$(cat <<DIFFEOF
### ${module} > ${label} — ${severity}

${details}

<details>
<summary>Legacy response (HTTP ${l_status})</summary>

\`\`\`json
$(echo "${l_body}" | jq . 2>/dev/null || echo "${l_body}")
\`\`\`

</details>

<details>
<summary>Kotlin response (HTTP ${k_status})</summary>

\`\`\`json
$(echo "${k_body}" | jq . 2>/dev/null || echo "${k_body}")
\`\`\`

</details>

---

DIFFEOF
)
        DIFF_SECTIONS+=("${diff_block}")
    fi
}

###############################################################################
# 1. Health Checks
###############################################################################

log_step "Health Checks"

log_info "Checking legacy server health..."
LEGACY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${LEGACY_BASE}/api/v1/docs" || echo "000")
if [[ "${LEGACY_HEALTH}" == "200" ]]; then
    log_ok "Legacy server is healthy (HTTP ${LEGACY_HEALTH})"
else
    log_err "Legacy server health check failed (HTTP ${LEGACY_HEALTH})"
    echo "Cannot proceed without legacy server. Exiting."
    exit 1
fi

log_info "Checking Kotlin server health..."
KOTLIN_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${KOTLIN_BASE}/api/health" || echo "000")
if [[ "${KOTLIN_HEALTH}" == "200" ]]; then
    log_ok "Kotlin server is healthy (HTTP ${KOTLIN_HEALTH})"
else
    log_err "Kotlin server health check failed (HTTP ${KOTLIN_HEALTH})"
    echo "Cannot proceed without Kotlin server. Exiting."
    exit 1
fi

###############################################################################
# 2. Account Setup & Login
###############################################################################

log_step "Account Setup & Login"

LOGIN_BODY=$(cat <<'EOF'
{
    "user_email": "test_compare@example.com",
    "user_password": "compare1234",
    "user_fcm": "fcm-compare-test",
    "user_social_id": "",
    "user_social_type": ""
}
EOF
)

# Try logging in to legacy first
log_info "Attempting login on legacy server..."
LEGACY_LOGIN_RAW=$(api_call POST "${LEGACY_API}/auth/login" -d "${LOGIN_BODY}")
LEGACY_LOGIN_STATUS=$(head -1 <<< "${LEGACY_LOGIN_RAW}")
LEGACY_LOGIN_BODY=$(tail -n +2 <<< "${LEGACY_LOGIN_RAW}")

if [[ "${LEGACY_LOGIN_STATUS}" != "200" ]]; then
    log_info "Login failed (HTTP ${LEGACY_LOGIN_STATUS}). Creating account via legacy signup..."
    SIGNUP_RAW=$(api_call POST "${LEGACY_API}/auth/" -d "${LOGIN_BODY}")
    SIGNUP_STATUS=$(head -1 <<< "${SIGNUP_RAW}")
    SIGNUP_BODY=$(tail -n +2 <<< "${SIGNUP_RAW}")

    if [[ "${SIGNUP_STATUS}" == "201" ]] || [[ "${SIGNUP_STATUS}" == "200" ]]; then
        log_ok "Account created successfully"
    else
        log_err "Failed to create account (HTTP ${SIGNUP_STATUS})"
        echo "${SIGNUP_BODY}" | jq . 2>/dev/null || echo "${SIGNUP_BODY}"
        echo "Cannot proceed without a test account. Exiting."
        exit 1
    fi

    # Login again after signup
    log_info "Logging in to legacy after signup..."
    LEGACY_LOGIN_RAW=$(api_call POST "${LEGACY_API}/auth/login" -d "${LOGIN_BODY}")
    LEGACY_LOGIN_STATUS=$(head -1 <<< "${LEGACY_LOGIN_RAW}")
    LEGACY_LOGIN_BODY=$(tail -n +2 <<< "${LEGACY_LOGIN_RAW}")
fi

LEGACY_TOKEN=$(echo "${LEGACY_LOGIN_BODY}" | jq -r '.data.access_token // .data.token // empty' 2>/dev/null)
if [[ -z "${LEGACY_TOKEN}" ]]; then
    log_err "Could not extract legacy token from login response"
    echo "${LEGACY_LOGIN_BODY}" | jq . 2>/dev/null || echo "${LEGACY_LOGIN_BODY}"
    exit 1
fi
log_ok "Legacy login successful (token: ${LEGACY_TOKEN:0:20}...)"

# Login to Kotlin
log_info "Logging in to Kotlin server..."
KOTLIN_LOGIN_RAW=$(api_call POST "${KOTLIN_API}/auth/login" -d "${LOGIN_BODY}")
KOTLIN_LOGIN_STATUS=$(head -1 <<< "${KOTLIN_LOGIN_RAW}")
KOTLIN_LOGIN_BODY=$(tail -n +2 <<< "${KOTLIN_LOGIN_RAW}")

KOTLIN_TOKEN=$(echo "${KOTLIN_LOGIN_BODY}" | jq -r '.data.access_token // .data.token // empty' 2>/dev/null)
if [[ -z "${KOTLIN_TOKEN}" ]]; then
    log_err "Could not extract Kotlin token from login response"
    echo "${KOTLIN_LOGIN_BODY}" | jq . 2>/dev/null || echo "${KOTLIN_LOGIN_BODY}"
    exit 1
fi
log_ok "Kotlin login successful (token: ${KOTLIN_TOKEN:0:20}...)"

# Compare login responses structurally
classify_diff "Auth" "POST /auth/login" \
    "${LEGACY_LOGIN_STATUS}" "${LEGACY_LOGIN_BODY}" \
    "${KOTLIN_LOGIN_STATUS}" "${KOTLIN_LOGIN_BODY}"

###############################################################################
# 3. GET Endpoint Comparisons
###############################################################################

log_step "GET Endpoints — Auth"

compare_endpoint "Auth" "GET /auth (profile)" GET "/auth/"

log_step "GET Endpoints — Garden"

compare_endpoint "Garden" "GET /garden/list" GET "/garden/list"

# Get garden_no from the list response for detail query
GARDEN_NO=$(echo "${LAST_LEGACY_BODY}" | jq -r '.data[0].garden_no // empty' 2>/dev/null)
if [[ -z "${GARDEN_NO}" ]]; then
    GARDEN_NO=$(echo "${LAST_KOTLIN_BODY}" | jq -r '.data[0].garden_no // empty' 2>/dev/null)
fi

if [[ -n "${GARDEN_NO}" ]]; then
    compare_endpoint "Garden" "GET /garden/detail?garden_no=${GARDEN_NO}" GET "/garden/detail?garden_no=${GARDEN_NO}"
else
    log_warn "No garden found — skipping garden detail test"
fi

log_step "GET Endpoints — Book"

# Book search (URL-encoded Korean query)
compare_endpoint "Book" "GET /book/search?query=클린" GET "/book/search?query=%ED%81%B4%EB%A6%B0"

# Book search by ISBN
compare_endpoint "Book" "GET /book/search-isbn?query=9788966262595" GET "/book/search-isbn?query=9788966262595"

# Book detail by ISBN
compare_endpoint "Book" "GET /book/detail-isbn?query=9788966262595" GET "/book/detail-isbn?query=9788966262595"

# Book status (may be empty for new user)
if [[ -n "${GARDEN_NO}" ]]; then
    compare_endpoint "Book" "GET /book/status?garden_no=${GARDEN_NO}" GET "/book/status?garden_no=${GARDEN_NO}"
else
    compare_endpoint "Book" "GET /book/status (no garden filter)" GET "/book/status"
fi

# Book duplication check
compare_endpoint "Book" "GET /book?isbn=9788966262595 (duplication)" GET "/book?isbn=9788966262595"

log_step "GET Endpoints — Memo"

compare_endpoint "Memo" "GET /memo (list)" GET "/memo/"

log_step "GET Endpoints — Push"

compare_endpoint "Push" "GET /push (settings)" GET "/push/"

###############################################################################
# 4. Write Endpoint Comparisons (create → compare → cleanup)
###############################################################################

log_step "Write Endpoints — Garden CRUD"

# Create garden on legacy
GARDEN_CREATE_BODY='{"garden_title":"Compare Test Garden","garden_info":"auto-test","garden_color":"blue"}'

log_info "Creating garden on legacy..."
compare_endpoint "Garden" "POST /garden (create)" POST "/garden/" --body "${GARDEN_CREATE_BODY}"
LEGACY_GARDEN_NO=$(echo "${LAST_LEGACY_BODY}" | jq -r '.data.garden_no // empty' 2>/dev/null)

log_info "Creating garden on Kotlin..."
GARDEN_CREATE_BODY_K='{"garden_title":"Compare Test Garden K","garden_info":"auto-test-k","garden_color":"blue"}'
# Call Kotlin directly to get its garden_no for cleanup
KOTLIN_GARDEN_RAW=$(api_call POST "${KOTLIN_API}/garden/" \
    -H "Authorization: Bearer ${KOTLIN_TOKEN}" \
    -d "${GARDEN_CREATE_BODY_K}")
KOTLIN_GARDEN_STATUS=$(head -1 <<< "${KOTLIN_GARDEN_RAW}")
KOTLIN_GARDEN_BODY=$(tail -n +2 <<< "${KOTLIN_GARDEN_RAW}")
KOTLIN_GARDEN_NO=$(echo "${KOTLIN_GARDEN_BODY}" | jq -r '.data.garden_no // empty' 2>/dev/null)

# Update garden — compare via both servers using legacy-created garden
if [[ -n "${LEGACY_GARDEN_NO}" ]]; then
    GARDEN_UPDATE_BODY='{"garden_title":"Updated Title","garden_info":"updated info","garden_color":"green"}'
    compare_endpoint "Garden" "PUT /garden?garden_no (update)" PUT "/garden/?garden_no=${LEGACY_GARDEN_NO}" --body "${GARDEN_UPDATE_BODY}"
fi

# Delete — cleanup both created gardens
if [[ -n "${LEGACY_GARDEN_NO}" ]]; then
    log_info "Cleaning up legacy-created garden (${LEGACY_GARDEN_NO})..."
    api_call DELETE "${LEGACY_API}/garden/?garden_no=${LEGACY_GARDEN_NO}" \
        -H "Authorization: Bearer ${LEGACY_TOKEN}" > /dev/null 2>&1 || true
fi
if [[ -n "${KOTLIN_GARDEN_NO}" ]]; then
    log_info "Cleaning up kotlin-created garden (${KOTLIN_GARDEN_NO})..."
    api_call DELETE "${KOTLIN_API}/garden/?garden_no=${KOTLIN_GARDEN_NO}" \
        -H "Authorization: Bearer ${KOTLIN_TOKEN}" > /dev/null 2>&1 || true
fi

log_step "Write Endpoints — Book CRUD"

# We need a garden_no. Re-fetch garden list to get the default garden.
GARDEN_LIST_RAW=$(api_call GET "${LEGACY_API}/garden/list" -H "Authorization: Bearer ${LEGACY_TOKEN}")
GARDEN_LIST_BODY=$(tail -n +2 <<< "${GARDEN_LIST_RAW}")
DEFAULT_GARDEN_NO=$(echo "${GARDEN_LIST_BODY}" | jq -r '.data[0].garden_no // empty' 2>/dev/null)

if [[ -z "${DEFAULT_GARDEN_NO}" ]]; then
    log_warn "No default garden found — skipping book CRUD tests"
else
    # Create book on legacy (null ISBN to avoid duplication)
    BOOK_CREATE_BODY=$(cat <<BOOKEOF
{
    "book_isbn": null,
    "garden_no": ${DEFAULT_GARDEN_NO},
    "book_title": "Legacy Compare Test Book",
    "book_info": "Auto test book",
    "book_author": "Test Author",
    "book_publisher": "Test Publisher",
    "book_tree": null,
    "book_image_url": null,
    "book_status": 1,
    "book_page": 200
}
BOOKEOF
)

    log_info "Creating book on legacy..."
    compare_endpoint "Book" "POST /book (create)" POST "/book/" --body "${BOOK_CREATE_BODY}"
    LEGACY_BOOK_NO=$(echo "${LAST_LEGACY_BODY}" | jq -r '.data.book_no // empty' 2>/dev/null)

    # Create book on Kotlin (null ISBN to avoid duplication)
    BOOK_CREATE_BODY_K=$(cat <<BOOKEOF2
{
    "book_isbn": null,
    "garden_no": ${DEFAULT_GARDEN_NO},
    "book_title": "Kotlin Compare Test Book",
    "book_info": "Auto test book k",
    "book_author": "Test Author K",
    "book_publisher": "Test Publisher K",
    "book_tree": null,
    "book_image_url": null,
    "book_status": 1,
    "book_page": 250
}
BOOKEOF2
)

    KOTLIN_BOOK_RAW=$(api_call POST "${KOTLIN_API}/book/" \
        -H "Authorization: Bearer ${KOTLIN_TOKEN}" \
        -d "${BOOK_CREATE_BODY_K}")
    KOTLIN_BOOK_STATUS=$(head -1 <<< "${KOTLIN_BOOK_RAW}")
    KOTLIN_BOOK_BODY=$(tail -n +2 <<< "${KOTLIN_BOOK_RAW}")
    KOTLIN_BOOK_NO=$(echo "${KOTLIN_BOOK_BODY}" | jq -r '.data.book_no // empty' 2>/dev/null)

    # Update book (using legacy-created book)
    if [[ -n "${LEGACY_BOOK_NO}" ]]; then
        BOOK_UPDATE_BODY='{"book_status": 2, "book_title": "Updated Title"}'
        compare_endpoint "Book" "PUT /book?book_no (update)" PUT "/book/?book_no=${LEGACY_BOOK_NO}" --body "${BOOK_UPDATE_BODY}"
    fi

    # Book read record — create
    if [[ -n "${LEGACY_BOOK_NO}" ]]; then
        READ_CREATE_BODY=$(cat <<READEOF
{
    "book_no": ${LEGACY_BOOK_NO},
    "book_start_date": "2026-04-10T09:00:00",
    "book_end_date": "2026-04-10T11:00:00",
    "book_current_page": 50
}
READEOF
)
        compare_endpoint "Book" "POST /book/read (create read record)" POST "/book/read" --body "${READ_CREATE_BODY}"
        LEGACY_READ_ID=$(echo "${LAST_LEGACY_BODY}" | jq -r '.data.id // .data.read_no // empty' 2>/dev/null)

        # GET read records
        compare_endpoint "Book" "GET /book/read?book_no=${LEGACY_BOOK_NO}" GET "/book/read?book_no=${LEGACY_BOOK_NO}"

        # Delete read record
        if [[ -n "${LEGACY_READ_ID}" ]]; then
            compare_endpoint "Book" "DELETE /book/read?id=${LEGACY_READ_ID}" DELETE "/book/read?id=${LEGACY_READ_ID}"
        fi
    fi

    # Cleanup books
    if [[ -n "${LEGACY_BOOK_NO}" ]]; then
        log_info "Cleaning up legacy-created book (${LEGACY_BOOK_NO})..."
        api_call DELETE "${LEGACY_API}/book/?book_no=${LEGACY_BOOK_NO}" \
            -H "Authorization: Bearer ${LEGACY_TOKEN}" > /dev/null 2>&1 || true
    fi
    if [[ -n "${KOTLIN_BOOK_NO}" ]]; then
        log_info "Cleaning up kotlin-created book (${KOTLIN_BOOK_NO})..."
        api_call DELETE "${KOTLIN_API}/book/?book_no=${KOTLIN_BOOK_NO}" \
            -H "Authorization: Bearer ${KOTLIN_TOKEN}" > /dev/null 2>&1 || true
    fi
fi

log_step "Write Endpoints — Memo CRUD"

# Need a book to attach memos to — create a temporary one
if [[ -n "${DEFAULT_GARDEN_NO}" ]]; then
    MEMO_BOOK_BODY=$(cat <<MEMOBOOKEOF
{
    "book_isbn": null,
    "garden_no": ${DEFAULT_GARDEN_NO},
    "book_title": "Memo Test Book",
    "book_info": "For memo testing",
    "book_author": "Memo Author",
    "book_publisher": "Memo Publisher",
    "book_tree": null,
    "book_image_url": null,
    "book_status": 1,
    "book_page": 100
}
MEMOBOOKEOF
)

    MEMO_BOOK_RAW=$(api_call POST "${LEGACY_API}/book/" \
        -H "Authorization: Bearer ${LEGACY_TOKEN}" \
        -d "${MEMO_BOOK_BODY}")
    MEMO_BOOK_BODY_RESP=$(tail -n +2 <<< "${MEMO_BOOK_RAW}")
    MEMO_BOOK_NO=$(echo "${MEMO_BOOK_BODY_RESP}" | jq -r '.data.book_no // empty' 2>/dev/null)

    if [[ -n "${MEMO_BOOK_NO}" ]]; then
        # Create memo on legacy
        MEMO_CREATE_BODY=$(cat <<MEMOEOF
{
    "book_no": ${MEMO_BOOK_NO},
    "memo_content": "Legacy test memo content"
}
MEMOEOF
)
        compare_endpoint "Memo" "POST /memo (create)" POST "/memo/" --body "${MEMO_CREATE_BODY}"
        LEGACY_MEMO_ID=$(echo "${LAST_LEGACY_BODY}" | jq -r '.data.memo_no // .data.id // empty' 2>/dev/null)

        # Create memo on Kotlin
        MEMO_CREATE_BODY_K=$(cat <<MEMOEOF2
{
    "book_no": ${MEMO_BOOK_NO},
    "memo_content": "Kotlin test memo content"
}
MEMOEOF2
)
        KOTLIN_MEMO_RAW=$(api_call POST "${KOTLIN_API}/memo/" \
            -H "Authorization: Bearer ${KOTLIN_TOKEN}" \
            -d "${MEMO_CREATE_BODY_K}")
        KOTLIN_MEMO_BODY_RESP=$(tail -n +2 <<< "${KOTLIN_MEMO_RAW}")
        KOTLIN_MEMO_ID=$(echo "${KOTLIN_MEMO_BODY_RESP}" | jq -r '.data.memo_no // .data.id // empty' 2>/dev/null)

        # Memo detail
        if [[ -n "${LEGACY_MEMO_ID}" ]]; then
            compare_endpoint "Memo" "GET /memo/detail?id=${LEGACY_MEMO_ID}" GET "/memo/detail?id=${LEGACY_MEMO_ID}"
        fi

        # Memo update
        if [[ -n "${LEGACY_MEMO_ID}" ]]; then
            MEMO_UPDATE_BODY=$(cat <<MEMOUPDEOF
{
    "book_no": ${MEMO_BOOK_NO},
    "memo_content": "Updated memo content"
}
MEMOUPDEOF
)
            compare_endpoint "Memo" "PUT /memo?id=${LEGACY_MEMO_ID} (update)" PUT "/memo/?id=${LEGACY_MEMO_ID}" --body "${MEMO_UPDATE_BODY}"
        fi

        # Memo like toggle
        if [[ -n "${LEGACY_MEMO_ID}" ]]; then
            compare_endpoint "Memo" "PUT /memo/like?id=${LEGACY_MEMO_ID} (toggle)" PUT "/memo/like?id=${LEGACY_MEMO_ID}"
        fi

        # Cleanup memos
        if [[ -n "${LEGACY_MEMO_ID}" ]]; then
            log_info "Cleaning up legacy-created memo (${LEGACY_MEMO_ID})..."
            api_call DELETE "${LEGACY_API}/memo/?id=${LEGACY_MEMO_ID}" \
                -H "Authorization: Bearer ${LEGACY_TOKEN}" > /dev/null 2>&1 || true
        fi
        if [[ -n "${KOTLIN_MEMO_ID}" ]]; then
            log_info "Cleaning up kotlin-created memo (${KOTLIN_MEMO_ID})..."
            api_call DELETE "${KOTLIN_API}/memo/?id=${KOTLIN_MEMO_ID}" \
                -H "Authorization: Bearer ${KOTLIN_TOKEN}" > /dev/null 2>&1 || true
        fi

        # Cleanup the memo-test book
        log_info "Cleaning up memo-test book (${MEMO_BOOK_NO})..."
        api_call DELETE "${LEGACY_API}/book/?book_no=${MEMO_BOOK_NO}" \
            -H "Authorization: Bearer ${LEGACY_TOKEN}" > /dev/null 2>&1 || true
    else
        log_warn "Could not create book for memo tests — skipping memo CRUD"
    fi
else
    log_warn "No default garden — skipping memo CRUD tests"
fi

log_step "Write Endpoints — Push"

# Push update
PUSH_UPDATE_BODY='{"push_app_ok": true, "push_book_ok": false, "push_time": "2026-04-14T21:00:00"}'
compare_endpoint "Push" "PUT /push (update settings)" PUT "/push/" --body "${PUSH_UPDATE_BODY}"

###############################################################################
# 5. Generate Markdown Report
###############################################################################

log_step "Generating Report"

# Build module summary using temp file (bash 3.x compatible — no associative arrays)
MODULE_COUNTS_FILE="${TMPDIR_COMP}/module_counts.txt"
: > "${MODULE_COUNTS_FILE}"
for result in "${MODULE_RESULTS[@]}"; do
    mod=$(echo "${result}" | cut -d'|' -f1)
    sev=$(echo "${result}" | cut -d'|' -f3)
    echo "${mod}|${sev}" >> "${MODULE_COUNTS_FILE}"
done

# Unique modules in order
MODULES_ORDERED=()
for result in "${MODULE_RESULTS[@]}"; do
    mod=$(echo "${result}" | cut -d'|' -f1)
    local_found=0
    for existing in "${MODULES_ORDERED[@]+"${MODULES_ORDERED[@]}"}"; do
        if [[ "${existing}" == "${mod}" ]]; then local_found=1; break; fi
    done
    if [[ ${local_found} -eq 0 ]]; then
        MODULES_ORDERED+=("${mod}")
    fi
done

# Write report
mkdir -p "$(dirname "${REPORT_FILE}")"
cat > "${REPORT_FILE}" <<REPORTEOF
# Legacy vs Kotlin API Comparison Report

**Generated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Legacy server:** ${LEGACY_BASE}
**Kotlin server:** ${KOTLIN_BASE}

## Summary

| Metric | Count |
|--------|-------|
| Total endpoints tested | ${TOTAL} |
| Match | ${MATCH} |
| Breaking differences | ${BREAKING} |
| Behavioral differences | ${BEHAVIORAL} |
| Cosmetic differences | ${COSMETIC} |

### Classification Key

- **MATCH**: Responses are structurally and semantically identical
- **BREAKING**: HTTP status, resp_code, or data structure mismatch (keys missing in Kotlin)
- **BEHAVIORAL**: resp_msg or static value differences
- **COSMETIC**: Extra fields in response, null vs empty string, etc.

## Module Summary

| Module | Total | Match | Breaking | Behavioral | Cosmetic |
|--------|-------|-------|----------|------------|----------|
REPORTEOF

for mod in "${MODULES_ORDERED[@]}"; do
    t=$(grep -c "^${mod}|" "${MODULE_COUNTS_FILE}" || echo 0)
    m=$(grep -c "^${mod}|MATCH" "${MODULE_COUNTS_FILE}" || echo 0)
    b=$(grep -c "^${mod}|BREAKING" "${MODULE_COUNTS_FILE}" || echo 0)
    bh=$(grep -c "^${mod}|BEHAVIORAL" "${MODULE_COUNTS_FILE}" || echo 0)
    c=$(grep -c "^${mod}|COSMETIC" "${MODULE_COUNTS_FILE}" || echo 0)
    echo "| ${mod} | ${t} | ${m} | ${b} | ${bh} | ${c} |" >> "${REPORT_FILE}"
done

cat >> "${REPORT_FILE}" <<'REPORTEOF2'

## Endpoint Results

| Module | Endpoint | Result | Details |
|--------|----------|--------|---------|
REPORTEOF2

for result in "${MODULE_RESULTS[@]}"; do
    mod=$(echo "${result}" | cut -d'|' -f1)
    ep=$(echo "${result}" | cut -d'|' -f2)
    sev=$(echo "${result}" | cut -d'|' -f3)
    det=$(echo "${result}" | cut -d'|' -f4-)
    # Emoji indicator
    case "${sev}" in
        MATCH)      icon="✅" ;;
        COSMETIC)   icon="🔵" ;;
        BEHAVIORAL) icon="🟡" ;;
        BREAKING)   icon="🔴" ;;
        *)          icon="❓" ;;
    esac
    echo "| ${mod} | ${ep} | ${icon} ${sev} | ${det:-—} |" >> "${REPORT_FILE}"
done

# Diff details
if [[ ${#DIFF_SECTIONS[@]} -gt 0 ]]; then
    cat >> "${REPORT_FILE}" <<'DIFFHDR'

## Detailed Differences

DIFFHDR
    for section in "${DIFF_SECTIONS[@]}"; do
        echo "${section}" >> "${REPORT_FILE}"
    done
fi

# Footer
cat >> "${REPORT_FILE}" <<'FOOTER'

---

*Report generated by `scripts/legacy-comparison/compare.sh`*
FOOTER

log_ok "Report written to ${REPORT_FILE}"

# ── Final summary ────────────────────────────────────────────────────────────
log_step "Final Summary"
echo -e "  Total:      ${BOLD}${TOTAL}${RESET}"
echo -e "  ${GREEN}Match:      ${MATCH}${RESET}"
echo -e "  ${RED}Breaking:   ${BREAKING}${RESET}"
echo -e "  ${YELLOW}Behavioral: ${BEHAVIORAL}${RESET}"
echo -e "  ${CYAN}Cosmetic:   ${COSMETIC}${RESET}"
echo ""

if [[ ${BREAKING} -gt 0 ]]; then
    echo -e "${RED}${BOLD}⚠ ${BREAKING} BREAKING difference(s) found. Review the report for details.${RESET}"
    exit 2
elif [[ ${BEHAVIORAL} -gt 0 ]]; then
    echo -e "${YELLOW}${BOLD}⚠ ${BEHAVIORAL} BEHAVIORAL difference(s) found. Review the report for details.${RESET}"
    exit 0
else
    echo -e "${GREEN}${BOLD}All endpoints match!${RESET}"
    exit 0
fi
