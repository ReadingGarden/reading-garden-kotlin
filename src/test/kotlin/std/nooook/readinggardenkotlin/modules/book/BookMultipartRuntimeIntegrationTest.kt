package std.nooook.readinggardenkotlin.modules.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookMultipartRuntimeIntegrationTest(
    @LocalServerPort private val port: Int,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
) {
    companion object {
        private val imagesRoot: Path = Files.createTempDirectory("reading-garden-runtime-images")

        @JvmStatic
        @DynamicPropertySource
        fun registerStorageProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.storage.images-root") { imagesRoot.toString() }
            registry.add("spring.servlet.multipart.location") { imagesRoot.resolve("multipart-temp").toString() }
        }
    }

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        cleanImagesRoot()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `runtime multipart upload should accept two megabytes and use external temp directory`() {
        val email = "runtimeupload@example.com"
        val accessToken = signupAndGetAccessToken(email)
        val userNo = checkNotNull(userRepository.findByUserEmail(email)?.userNo)
        val bookNo = bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "런타임 업로드 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookInfo = "소개",
                bookStatus = 0,
                bookPage = 100,
            ),
        ).bookNo ?: error("bookNo was not generated")
        val fileBytes = ByteArray(2 * 1024 * 1024) { 1 }
        val boundary = "----ReadingGardenBoundary${System.nanoTime()}"
        val requestBody = buildMultipartBody(boundary, "cover.png", "image/png", fileBytes)

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI("http://127.0.0.1:$port/api/v1/book/image?book_no=$bookNo"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(201, response.statusCode())
        val body = objectMapper.readTree(response.body())
        assertEquals(201, body.path("resp_code").asInt())
        assertEquals("이미지 업로드 성공", body.path("resp_msg").asText())

        val image = checkNotNull(bookImageRepository.findByBookNo(bookNo))
        assertTrue(Files.exists(imagesRoot.resolve(image.imageUrl)))
        assertTrue(Files.isDirectory(imagesRoot.resolve("multipart-temp")))
    }

    private fun signupAndGetAccessToken(email: String): String {
        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI("http://127.0.0.1:$port/api/v1/auth"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"user_email":"$email","user_password":"pw1234","user_fcm":"fcm-token","user_social_id":"","user_social_type":""}""",
                    ),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(201, response.statusCode())
        return objectMapper.readTree(response.body())
            .path("data")
            .path("access_token")
            .asText()
    }

    private fun buildMultipartBody(
        boundary: String,
        filename: String,
        contentType: String,
        fileBytes: ByteArray,
    ): ByteArray {
        val header = buildString {
            append("--")
            append(boundary)
            append("\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            append(filename)
            append("\"\r\n")
            append("Content-Type: ")
            append(contentType)
            append("\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        return header + fileBytes + footer
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
}
