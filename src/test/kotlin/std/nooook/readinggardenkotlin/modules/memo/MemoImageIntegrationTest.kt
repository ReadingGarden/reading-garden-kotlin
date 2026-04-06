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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@SpringBootTest
@AutoConfigureMockMvc
class MemoImageIntegrationTest(
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

    @Test
    fun `upload memo image should persist file and row`() {
        val accessToken = signupAndGetAccessToken("memoimageupload@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoimageupload@example.com")?.userNo)
        val memoId = createMemo(userNo, "이미지 업로드 메모")
        val file = MockMultipartFile(
            "file",
            "cover.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val image = checkNotNull(memoImageRepository.findByMemoNo(memoId))
        assertTrue(image.imageUrl.startsWith("memo/"))
        assertTrue(Files.exists(imagesRoot.resolve(image.imageUrl)))
    }

    @Test
    fun `upload memo image should replace existing file and keep one row`() {
        val accessToken = signupAndGetAccessToken("memoimagereplace@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoimagereplace@example.com")?.userNo)
        val memoId = createMemo(userNo, "이미지 교체 메모")
        val firstFile = MockMultipartFile(
            "file",
            "first.png",
            MediaType.IMAGE_PNG_VALUE,
            "first-image".toByteArray(),
        )
        val secondFile = MockMultipartFile(
            "file",
            "second.png",
            MediaType.IMAGE_PNG_VALUE,
            "second-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(firstFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)

        val firstImage = checkNotNull(memoImageRepository.findByMemoNo(memoId))
        val firstStoredPath = imagesRoot.resolve(firstImage.imageUrl)
        assertTrue(Files.exists(firstStoredPath))

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(secondFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val replacedImage = checkNotNull(memoImageRepository.findByMemoNo(memoId))
        assertFalse(Files.exists(firstStoredPath))
        assertTrue(Files.exists(imagesRoot.resolve(replacedImage.imageUrl)))
        assertEquals(1, memoImageRepository.count())
        assertFalse(firstImage.imageUrl == replacedImage.imageUrl)
    }

    @Test
    fun `upload memo image should reject files over five megabytes`() {
        val accessToken = signupAndGetAccessToken("memoimageoversize@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoimageoversize@example.com")?.userNo)
        val memoId = createMemo(userNo, "큰 이미지 메모")
        val oversizedBytes = ByteArray(5 * 1024 * 1024 + 1)
        val file = MockMultipartFile(
            "file",
            "too-big.png",
            MediaType.IMAGE_PNG_VALUE,
            oversizedBytes,
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("이미지 용량은 5MB를 초과할 수 없습니다."))

        assertTrue(memoImageRepository.findByMemoNo(memoId) == null)
    }

    @Test
    fun `delete memo image should remove file and record`() {
        val accessToken = signupAndGetAccessToken("memoimagedelete@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoimagedelete@example.com")?.userNo)
        val memoId = createMemo(userNo, "이미지 삭제 메모")
        val file = MockMultipartFile(
            "file",
            "delete.png",
            MediaType.IMAGE_PNG_VALUE,
            "delete-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)

        val storedImage = checkNotNull(memoImageRepository.findByMemoNo(memoId))
        val storedPath = imagesRoot.resolve(storedImage.imageUrl)
        assertTrue(Files.exists(storedPath))

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))

        assertTrue(memoImageRepository.findByMemoNo(memoId) == null)
        assertFalse(Files.exists(storedPath))
    }

    @Test
    fun `delete memo image should return bad request when image is missing`() {
        val accessToken = signupAndGetAccessToken("memoimagemissing@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memoimagemissing@example.com")?.userNo)
        val memoId = createMemo(userNo, "이미지 없는 메모")

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 이미지가 없습니다."))
    }

    @Test
    fun `delete memo image should return bad request when memo is missing`() {
        val accessToken = signupAndGetAccessToken("memoimagemissingmemo@example.com")

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    private fun createMemo(
        userNo: Int,
        memoContent: String,
    ): Int {
        val bookNo = bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "$memoContent 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookInfo = "소개",
                bookStatus = 0,
                bookPage = 100,
            ),
        ).bookNo ?: error("bookNo was not generated")

        return checkNotNull(
            memoRepository.save(
                MemoEntity(
                    bookNo = bookNo,
                    memoContent = memoContent,
                    userNo = userNo,
                    memoLike = false,
                ),
            ).id,
        )
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
