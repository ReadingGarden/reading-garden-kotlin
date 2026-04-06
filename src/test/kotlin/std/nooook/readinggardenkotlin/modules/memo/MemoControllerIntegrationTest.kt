package std.nooook.readinggardenkotlin.modules.memo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository

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

    @BeforeEach
    fun setUp() {
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        userRepository.deleteAll()
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
