package std.nooook.readinggardenkotlin.common.docs

object OpenApiExamples {
    const val BAD_REQUEST = """
        {
          "resp_code": 400,
          "resp_msg": "Request body validation failed.",
          "errors": [
            {
              "field": "user_email",
              "message": "must match legacy email format",
              "rejectedValue": "wrong-email"
            }
          ]
        }
    """

    const val UNAUTHORIZED = """
        {
          "resp_code": 401,
          "resp_msg": "Unauthorized",
          "errors": null
        }
    """

    const val APP_VERSION_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "앱 버전 조회 성공",
          "data": {
            "platform": "ios",
            "latest_version": "1.2.0",
            "min_supported_version": "1.0.0",
            "store_url": "https://apps.apple.com/app/id1234567890"
          }
        }
    """

    const val APP_VERSION_BAD_REQUEST = """
        {
          "resp_code": 400,
          "resp_msg": "platform must be one of: ios, android",
          "errors": null
        }
    """

    const val APP_VERSION_NOT_FOUND = """
        {
          "resp_code": 404,
          "resp_msg": "App version not found for platform: ios",
          "errors": null
        }
    """

    const val AUTH_SIGNUP_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "회원가입 성공",
          "data": {
            "access_token": "fixture-access-token",
            "refresh_token": "fixture-refresh-token",
            "user_nick": "임의닉네임"
          }
        }
    """

    const val AUTH_LOGIN_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "로그인 성공",
          "data": {
            "access_token": "fixture-access-token",
            "refresh_token": "fixture-refresh-token"
          }
        }
    """

    const val AUTH_LOGOUT_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "로그아웃 성공",
          "data": {}
        }
    """

    const val AUTH_REFRESH_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "토큰 발급 성공",
          "data": "fixture-access-token"
        }
    """

    const val AUTH_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "회원 탈퇴 성공",
          "data": {}
        }
    """

    const val AUTH_FIND_PASSWORD_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메일이 발송되었습니다. 확인해주세요.",
          "data": {}
        }
    """

    const val AUTH_FIND_PASSWORD_CHECK_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "인증 성공",
          "data": {}
        }
    """

    const val AUTH_PASSWORD_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "비밀번호 변경 성공",
          "errors": null
        }
    """

    const val AUTH_PROFILE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "조회 성공",
          "data": {
            "user_no": 1,
            "user_nick": "임의닉네임",
            "user_email": "user@example.com",
            "user_social_type": "",
            "user_image": "데이지",
            "user_created_at": "2026-04-09T16:00:00",
            "garden_count": 1,
            "read_book_count": 0,
            "like_book_count": 0
          }
        }
    """

    const val AUTH_PROFILE_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "프로필 변경 성공",
          "data": {
            "user_no": 1,
            "user_nick": "임의닉네임",
            "user_email": "user@example.com",
            "user_image": "데이지",
            "user_fcm": "fcm-token-value",
            "user_social_id": "",
            "user_social_type": "",
            "user_created_at": "2026-04-09T16:00:00"
          }
        }
    """

    const val BOOK_SEARCH_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 검색 성공",
          "data": {
            "query": "클린 코드",
            "startIndex": 1,
            "itemsPerPage": 100,
            "item": [
              {
                "title": "클린 코드 결과",
                "author": "로버트 C. 마틴",
                "isbn13": "9780132350884",
                "cover": "https://example.com/cover.jpg",
                "publisher": "프래그마틱"
              }
            ]
          }
        }
    """

    const val BOOK_DETAIL_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 상세 조회 성공",
          "data": {
            "searchCategoryId": 1,
            "searchCategoryName": "소설",
            "title": "상세 책",
            "author": "저자",
            "description": "소개",
            "isbn13": "9788937462788",
            "cover": "https://example.com/book.jpg",
            "publisher": "출판사",
            "itemPage": 321,
            "record": {},
            "memo": {}
          }
        }
    """

    const val GARDEN_LIST_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 리스트 조회 성공",
          "data": [
            {
              "garden_no": 10,
              "garden_title": "메인 가든",
              "garden_info": "첫 번째",
              "garden_color": "green",
              "garden_members": 2,
              "book_count": 1,
              "garden_created_at": "2024-01-05T08:30:00"
            }
          ]
        }
    """

    const val MEMO_DETAIL_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 상세 조회 성공",
          "data": {
            "id": 1,
            "book_no": 1,
            "book_title": "상세 메모 책",
            "book_author": "저자",
            "book_publisher": "출판사",
            "book_info": "메모 상세용 책 소개",
            "memo_content": "상세 메모 내용",
            "image_url": null,
            "memo_created_at": "2026-04-09T16:30:00"
          }
        }
    """

    const val PUSH_GET_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "푸시 알림 조회 성공",
          "data": {
            "user_no": 1,
            "push_app_ok": true,
            "push_book_ok": false,
            "push_time": null
          }
        }
    """

    const val PUSH_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "푸시 알림 수정 성공",
          "errors": null
        }
    """

    const val CREATED_EMPTY_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 업로드 성공",
          "errors": null
        }
    """
}
