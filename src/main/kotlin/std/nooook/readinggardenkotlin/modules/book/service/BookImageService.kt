package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository

@Service
class BookImageService(
    private val bookRepository: BookRepository,
    private val bookImageRepository: BookImageRepository,
    private val imageStorage: ImageStorage,
) {
    fun uploadBookImage(
        bookNo: Long,
        file: MultipartFile,
    ): String {
        val book = bookRepository.findById(bookNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책이 없습니다.") }

        val existingImage = bookImageRepository.findByBookId(bookNo)
        if (existingImage != null) {
            imageStorage.delete(existingImage.url)
            bookImageRepository.delete(existingImage)
        }

        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 용량은 5MB를 초과할 수 없습니다.")
        }

        val imageUrl = imageStorage.save("book", file.originalFilename ?: file.name, file.bytes)
        bookImageRepository.save(
            BookImageEntity(
                book = book,
                name = file.originalFilename ?: file.name,
                url = imageUrl,
            ),
        )

        return "이미지 업로드 성공"
    }

    fun deleteBookImage(bookNo: Long): String {
        bookRepository.findById(bookNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책이 없습니다.") }

        val existingImage = bookImageRepository.findByBookId(bookNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 이미지가 없습니다.")

        imageStorage.delete(existingImage.url)
        bookImageRepository.delete(existingImage)
        return "이미지 삭제 성공"
    }

    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L
    }
}
