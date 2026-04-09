package std.nooook.readinggardenkotlin.modules.memo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@SpringBootTest
@AutoConfigureMockMvc
class MemoControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    companion object {
        private val imagesRoot: Path = Files.createTempDirectory("reading-garden-memo-images")

        @JvmStatic
        @DynamicPropertySource
        fun registerStorageProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.storage.images-root") { imagesRoot.toString() }
            registry.add("spring.servlet.multipart.location") { imagesRoot.resolve("multipart-temp").toString() }
        }
    }

    @BeforeEach
    fun setUp() {
        cleanImagesRoot()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun cleanImagesRoot() {
        if (!Files.exists(imagesRoot)) {
            Files.createDirectories(imagesRoot)
            return
        }

        Files.walk(imagesRoot).use { paths ->
            paths
                .sorted(Comparator.reverseOrder())
                .filter { it != imagesRoot }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `get memo should require authentication`() {
        mockMvc.perform(
            get("/api/v1/memo/"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `get memo detail should require authentication`() {
        mockMvc.perform(
            get("/api/v1/memo/detail")
                .queryParam("id", "1"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `get memo should return current user's memos with legacy paging and ordering`() {
        val accessToken = signupAndGetAccessToken("memolist@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memolist@example.com")?.userNo)
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book1 = bookRepository.save(
            BookEntity(
                bookTitle = "첫 번째 책",
                bookAuthor = "저자 A",
                bookPublisher = "출판사 A",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 111,
                bookImageUrl = "https://example.com/book-a.jpg",
                bookInfo = "소개 A",
            ),
        )
        val book2 = bookRepository.save(
            BookEntity(
                bookTitle = "두 번째 책",
                bookAuthor = "저자 B",
                bookPublisher = "출판사 B",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 222,
                bookImageUrl = "https://example.com/book-b.jpg",
                bookInfo = "소개 B",
            ),
        )
        val book3 = bookRepository.save(
            BookEntity(
                bookTitle = "세 번째 책",
                bookAuthor = "저자 C",
                bookPublisher = "출판사 C",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 333,
                bookImageUrl = "https://example.com/book-c.jpg",
                bookInfo = "소개 C",
            ),
        )

        val memo1 = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book1.bookNo),
                memoContent = "메모 A",
                memoCreatedAt = now.minusHours(2),
                userNo = userNo,
                memoLike = true,
            ),
        )
        memoImageRepository.save(
            MemoImageEntity(
                imageName = "memo-a.png",
                imageUrl = "https://example.com/memo-a.png",
                imageCreatedAt = now.minusHours(2),
                memoNo = checkNotNull(memo1.id),
            ),
        )

        memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book2.bookNo),
                memoContent = "메모 B",
                memoCreatedAt = now.minusHours(1),
                userNo = userNo,
                memoLike = true,
            ),
        )

        memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book3.bookNo),
                memoContent = "메모 C",
                memoCreatedAt = now,
                userNo = userNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(3))
            .andExpect(jsonPath("$.data.list[0].memo_content").value("메모 B"))
            .andExpect(jsonPath("$.data.list[0].memo_like").value(true))
            .andExpect(jsonPath("$.data.list[0].image_url").value(""))
            .andExpect(jsonPath("$.data.list[0].book_title").value("두 번째 책"))
            .andExpect(jsonPath("$.data.list[1].memo_content").value("메모 A"))
            .andExpect(jsonPath("$.data.list[1].memo_like").value(true))
            .andExpect(jsonPath("$.data.list[1].image_url").value("https://example.com/memo-a.png"))
            .andExpect(jsonPath("$.data.list[1].book_title").value("첫 번째 책"))
            .andExpect(jsonPath("$.data.list[2].memo_content").value("메모 C"))
            .andExpect(jsonPath("$.data.list[2].memo_like").value(false))
            .andExpect(jsonPath("$.data.list[2].book_title").value("세 번째 책"))
            .andExpect(jsonPath("$.data.list[0].memo_created_at").exists())
            .andExpect(jsonPath("$.data.list[1].memo_created_at").exists())
            .andExpect(jsonPath("$.data.list[2].memo_created_at").exists())
    }

    @Test
    fun `get memo should ignore orphan memos in result and total`() {
        val accessToken = signupAndGetAccessToken("memoorphan@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoorphan@example.com")?.userNo)

        val book = bookRepository.save(
            BookEntity(
                bookTitle = "정상 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 100,
                bookImageUrl = "https://example.com/book.jpg",
                bookInfo = "소개",
            ),
        )

        memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book.bookNo),
                memoContent = "정상 메모",
                userNo = userNo,
                memoLike = true,
            ),
        )
        memoRepository.save(
            MemoEntity(
                bookNo = 999999,
                memoContent = "고아 메모",
                userNo = userNo,
                memoLike = true,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(1))
            .andExpect(jsonPath("$.data.list[0].memo_content").value("정상 메모"))
            .andExpect(jsonPath("$.data.list[0].book_title").value("정상 책"))
    }

    @Test
    fun `get memo detail should return latest image and legacy payload`() {
        val accessToken = signupAndGetAccessToken("memodetail@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memodetail@example.com")?.userNo)
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book = bookRepository.save(
            BookEntity(
                bookTitle = "상세 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 123,
                bookImageUrl = "https://example.com/book-detail.jpg",
                bookInfo = "책 소개",
            ),
        )
        val otherBook = bookRepository.save(
            BookEntity(
                bookTitle = "다른 책",
                bookAuthor = "다른 저자",
                bookPublisher = "다른 출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 456,
                bookImageUrl = "https://example.com/book-other.jpg",
                bookInfo = "다른 책 소개",
            ),
        )
        memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(otherBook.bookNo),
                memoContent = "첫 메모",
                memoCreatedAt = now.plusHours(1),
                userNo = userNo,
                memoLike = true,
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book.bookNo),
                memoContent = "상세 메모",
                memoCreatedAt = now.minusHours(1),
                userNo = userNo,
                memoLike = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        memoImageRepository.save(
            MemoImageEntity(
                imageName = "memo-old.png",
                imageUrl = "https://example.com/memo-old.png",
                imageCreatedAt = now.minusHours(1),
                memoNo = memoNo,
            ),
        )
        memoImageRepository.save(
            MemoImageEntity(
                imageName = "memo-new.png",
                imageUrl = "https://example.com/memo-new.png",
                imageCreatedAt = now,
                memoNo = memoNo,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 상세 조회 성공"))
            .andExpect(jsonPath("$.data.id").value(memoNo))
            .andExpect(jsonPath("$.data.book_no").value(checkNotNull(book.bookNo)))
            .andExpect(jsonPath("$.data.book_title").value("상세 책"))
            .andExpect(jsonPath("$.data.book_author").value("저자"))
            .andExpect(jsonPath("$.data.book_publisher").value("출판사"))
            .andExpect(jsonPath("$.data.book_info").value("책 소개"))
            .andExpect(jsonPath("$.data.memo_content").value("상세 메모"))
            .andExpect(jsonPath("$.data.image_url").value("https://example.com/memo-new.png"))
            .andExpect(jsonPath("$.data.memo_created_at").value("2026-04-06T11:00:00"))
    }

    @Test
    fun `get memo detail should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memoowner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memovisitor@example.com")
        val ownerUserNo = checkNotNull(userRepository.findByUserEmail("memoowner@example.com")?.userNo)
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book = bookRepository.save(
            BookEntity(
                bookTitle = "타인 메모 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = ownerUserNo,
                bookPage = 123,
                bookImageUrl = "https://example.com/book-owner.jpg",
                bookInfo = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book.bookNo),
                memoContent = "타인 메모",
                memoCreatedAt = now,
                userNo = ownerUserNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(memo.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `get memo detail should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memomissing@example.com")

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `create memo should create memo for owned book`() {
        val accessToken = signupAndGetAccessToken("memocreate@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memocreate@example.com")?.userNo)
        val book = bookRepository.save(
            BookEntity(
                bookTitle = "생성용 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 101,
                bookImageUrl = "https://example.com/create-book.jpg",
                bookInfo = "책 소개",
            ),
        )

        val response = mockMvc.perform(
            post("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(book.bookNo)},"memo_content":"새 메모"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("메모 추가 성공"))
            .andExpect(jsonPath("$.data.id").isNumber)
            .andReturn()

        val memoId = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("id")
            .asInt()

        val saved = checkNotNull(memoRepository.findById(memoId).orElse(null))
        assertEquals(userNo, saved.userNo)
        assertEquals(book.bookNo, saved.bookNo)
        assertEquals("새 메모", saved.memoContent)
        assertFalse(saved.memoLike)
    }

    @Test
    fun `create memo should return bad request when book belongs to another user`() {
        signupAndGetAccessToken("memo_owner_book@example.com")
        val accessToken = signupAndGetAccessToken("memo_create_visitor@example.com")
        val ownerUserNo = checkNotNull(userRepository.findByUserEmail("memo_owner_book@example.com")?.userNo)
        val ownerBook = bookRepository.save(
            BookEntity(
                bookTitle = "타인 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = ownerUserNo,
                bookPage = 202,
                bookImageUrl = "https://example.com/owner-book.jpg",
                bookInfo = "책 소개",
            ),
        )

        mockMvc.perform(
            post("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(ownerBook.bookNo)},"memo_content":"실패 메모"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `update memo should update memo when memo exists and book is owned`() {
        val accessToken = signupAndGetAccessToken("memoupdate@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoupdate@example.com")?.userNo)
        val sourceBook = bookRepository.save(
            BookEntity(
                bookTitle = "원본 책",
                bookAuthor = "저자 A",
                bookPublisher = "출판사 A",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 111,
                bookImageUrl = "https://example.com/update-book-a.jpg",
                bookInfo = "소개 A",
            ),
        )
        val targetBook = bookRepository.save(
            BookEntity(
                bookTitle = "수정 대상 책",
                bookAuthor = "저자 B",
                bookPublisher = "출판사 B",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 222,
                bookImageUrl = "https://example.com/update-book-b.jpg",
                bookInfo = "소개 B",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(sourceBook.bookNo),
                memoContent = "기존 메모",
                userNo = userNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", checkNotNull(memo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(targetBook.bookNo)},"memo_content":"수정된 메모"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 수정 성공"))

        val updated = checkNotNull(memoRepository.findById(checkNotNull(memo.id)).orElse(null))
        assertEquals(targetBook.bookNo, updated.bookNo)
        assertEquals("수정된 메모", updated.memoContent)
    }

    @Test
    fun `update memo should return bad request when target book belongs to another user`() {
        val accessToken = signupAndGetAccessToken("memoupdate_book_owner@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoupdate_book_owner@example.com")?.userNo)
        signupAndGetAccessToken("memoupdate_book_other@example.com")
        val otherUserNo = checkNotNull(userRepository.findByUserEmail("memoupdate_book_other@example.com")?.userNo)

        val ownedBook = bookRepository.save(
            BookEntity(
                bookTitle = "내 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 123,
                bookImageUrl = "https://example.com/owned-book.jpg",
                bookInfo = "내 책 소개",
            ),
        )
        val otherBook = bookRepository.save(
            BookEntity(
                bookTitle = "타인 책",
                bookAuthor = "타인 저자",
                bookPublisher = "타인 출판사",
                bookStatus = 1,
                userNo = otherUserNo,
                bookPage = 456,
                bookImageUrl = "https://example.com/other-book.jpg",
                bookInfo = "타인 책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(ownedBook.bookNo),
                memoContent = "업데이트 전",
                userNo = userNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", checkNotNull(memo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(otherBook.bookNo)},"memo_content":"실패"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `update memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memoupdate_missing@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoupdate_missing@example.com")?.userNo)
        val book = bookRepository.save(
            BookEntity(
                bookTitle = "업데이트 대상 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 777,
                bookImageUrl = "https://example.com/update-missing-book.jpg",
                bookInfo = "책 소개",
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(book.bookNo)},"memo_content":"없는 메모 수정"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `update memo should update another users memo like legacy behavior`() {
        val ownerAccessToken = signupAndGetAccessToken("memo_update_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_update_visitor@example.com")
        val ownerUserNo = checkNotNull(userRepository.findByUserEmail("memo_update_owner@example.com")?.userNo)
        val visitorUserNo = checkNotNull(userRepository.findByUserEmail("memo_update_visitor@example.com")?.userNo)

        val ownerBook = bookRepository.save(
            BookEntity(
                bookTitle = "소유자 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = ownerUserNo,
                bookPage = 123,
                bookImageUrl = "https://example.com/owner-update-book.jpg",
                bookInfo = "소유자 책 소개",
            ),
        )
        val visitorBook = bookRepository.save(
            BookEntity(
                bookTitle = "방문자 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = visitorUserNo,
                bookPage = 321,
                bookImageUrl = "https://example.com/visitor-update-book.jpg",
                bookInfo = "방문자 책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(ownerBook.bookNo),
                memoContent = "소유자 메모",
                userNo = ownerUserNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(visitorBook.bookNo)},"memo_content":"타인 메모 수정"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 수정 성공"))

        val updated = checkNotNull(memoRepository.findById(checkNotNull(ownerMemo.id)).orElse(null))
        assertEquals(ownerUserNo, updated.userNo)
        assertEquals(visitorBook.bookNo, updated.bookNo)
        assertEquals("타인 메모 수정", updated.memoContent)

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `delete memo should delete memo images rows and files`() {
        val accessToken = signupAndGetAccessToken("memodelete@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memodelete@example.com")?.userNo)
        val book = bookRepository.save(
            BookEntity(
                bookTitle = "삭제용 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 303,
                bookImageUrl = "https://example.com/delete-book.jpg",
                bookInfo = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book.bookNo),
                memoContent = "삭제 메모",
                userNo = userNo,
                memoLike = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)
        val imageRelativePath = "memo/delete/memo-delete.png"
        val storedPath = imagesRoot.resolve(imageRelativePath)
        Files.createDirectories(storedPath.parent)
        Files.writeString(storedPath, "memo image")
        memoImageRepository.save(
            MemoImageEntity(
                imageName = "memo-delete.png",
                imageUrl = imageRelativePath,
                memoNo = memoNo,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 삭제 성공"))

        assertTrue(memoRepository.findById(memoNo).isEmpty)
        assertTrue(memoImageRepository.findAllByMemoNoIn(listOf(memoNo)).isEmpty())
        assertFalse(Files.exists(storedPath))
    }

    @Test
    fun `delete memo should delete another users memo like legacy behavior`() {
        signupAndGetAccessToken("memo_delete_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_delete_visitor@example.com")
        val ownerUserNo = checkNotNull(userRepository.findByUserEmail("memo_delete_owner@example.com")?.userNo)

        val ownerBook = bookRepository.save(
            BookEntity(
                bookTitle = "삭제 소유자 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = ownerUserNo,
                bookPage = 404,
                bookImageUrl = "https://example.com/delete-owner-book.jpg",
                bookInfo = "책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(ownerBook.bookNo),
                memoContent = "삭제 대상 메모",
                userNo = ownerUserNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 삭제 성공"))

        assertTrue(memoRepository.findById(checkNotNull(ownerMemo.id)).isEmpty)
    }

    @Test
    fun `delete memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memodelete_missing@example.com")

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `like memo should toggle memo like both directions`() {
        val accessToken = signupAndGetAccessToken("memolike@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memolike@example.com")?.userNo)
        val book = bookRepository.save(
            BookEntity(
                bookTitle = "좋아요 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 505,
                bookImageUrl = "https://example.com/like-book.jpg",
                bookInfo = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(book.bookNo),
                memoContent = "좋아요 메모",
                userNo = userNo,
                memoLike = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))

        assertTrue(checkNotNull(memoRepository.findById(memoNo).orElse(null)).memoLike)

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))

        assertFalse(checkNotNull(memoRepository.findById(memoNo).orElse(null)).memoLike)
    }

    @Test
    fun `like memo should toggle another users memo like legacy behavior`() {
        signupAndGetAccessToken("memo_like_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_like_visitor@example.com")
        val ownerUserNo = checkNotNull(userRepository.findByUserEmail("memo_like_owner@example.com")?.userNo)

        val ownerBook = bookRepository.save(
            BookEntity(
                bookTitle = "좋아요 소유자 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = ownerUserNo,
                bookPage = 606,
                bookImageUrl = "https://example.com/like-owner-book.jpg",
                bookInfo = "책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                bookNo = checkNotNull(ownerBook.bookNo),
                memoContent = "타인 좋아요 메모",
                userNo = ownerUserNo,
                memoLike = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))

        assertTrue(checkNotNull(memoRepository.findById(checkNotNull(ownerMemo.id)).orElse(null)).memoLike)
    }

    @Test
    fun `like memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memolike_missing@example.com")

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    private fun signupAndGetAccessToken(email: String): String {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"pw1234","user_fcm":"fcm-token","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()
    }
}
