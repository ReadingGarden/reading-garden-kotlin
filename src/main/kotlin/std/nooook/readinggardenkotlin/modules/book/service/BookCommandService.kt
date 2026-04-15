package std.nooook.readinggardenkotlin.modules.book.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookRequest
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookResponse
import std.nooook.readinggardenkotlin.modules.book.controller.UpdateBookRequest
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class BookCommandService(
    private val bookRepository: BookRepository,
    private val gardenRepository: GardenRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createBook(
        userId: Long,
        request: CreateBookRequest,
    ): CreateBookResponse {
        if (request.garden_no != null) {
            val gardenNo = request.garden_no
            if (!gardenRepository.existsById(gardenNo)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
            }
            if (bookRepository.countByGardenId(gardenNo) >= MAX_GARDEN_BOOK_COUNT) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "책 생성 개수 초과")
            }
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.") }
        val garden = request.garden_no?.let { gardenRepository.findById(it).orElse(null) }

        val saved = bookRepository.save(
            BookEntity(
                garden = garden,
                title = request.book_title,
                author = request.book_author,
                publisher = request.book_publisher,
                status = request.book_status,
                user = user,
                page = request.book_page,
                isbn = request.book_isbn,
                tree = request.book_tree,
                imageUrl = request.book_image_url,
                info = request.book_info,
            ),
        )

        return CreateBookResponse(
            book_no = saved.id,
        )
    }

    @Transactional
    fun updateBook(
        userId: Long,
        bookNo: Long,
        request: UpdateBookRequest,
    ): String {
        val book = bookRepository.findByIdAndUserId(bookNo, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")

        request.garden_no?.let { targetGardenNo ->
            if (targetGardenNo != book.garden?.id && bookRepository.countByGardenId(targetGardenNo) >= MAX_GARDEN_BOOK_COUNT) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
            }
            book.garden = gardenRepository.findById(targetGardenNo).orElse(null)
        }
        request.book_tree?.let { book.tree = it }
        request.book_status?.let { book.status = it }
        request.book_title?.let { book.title = it }
        request.book_author?.let { book.author = it }
        request.book_image_url?.let { book.imageUrl = it }
        bookRepository.save(book)
        return "책 수정 성공"
    }

    @Transactional
    fun deleteBook(
        userId: Long,
        bookNo: Long,
    ): String {
        val book = bookRepository.findByIdAndUserId(bookNo, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            bookReadRepository.deleteAllByBookId(bookNo)

            bookImageRepository.findAllByBookId(bookNo).forEach { image ->
                stagedDeletes += imageStorage.stageDelete(image.url)
                bookImageRepository.delete(image)
            }

            val memos = memoRepository.findAllByBookId(bookNo)
            val memoIds = memos.map { it.id }
            if (memoIds.isNotEmpty()) {
                memoImageRepository.findAllByMemoIdIn(memoIds).forEach { memoImage ->
                    stagedDeletes += imageStorage.stageDelete(memoImage.url)
                    memoImageRepository.delete(memoImage)
                }
            }
            memos.forEach { memoRepository.delete(it) }

            bookRepository.delete(book)
            finalizeStagedDeletes(stagedDeletes)
            return "책 삭제 성공"
        } catch (exception: Exception) {
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to delete book resources.", exception)
            }
        }
    }

    private fun finalizeStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        if (stagedDeletes.isEmpty()) {
            return
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            commitStagedDeletes(stagedDeletes)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    commitStagedDeletes(stagedDeletes)
                }

                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        rollbackStagedDeletes(stagedDeletes)
                    }
                }
            },
        )
    }

    private fun commitStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        stagedDeletes.forEach { stagedDelete ->
            try {
                stagedDelete.commit()
            } catch (exception: Exception) {
                logger.warn("Failed to finalize staged image deletion.", exception)
            }
        }
    }

    private fun rollbackStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        stagedDeletes
            .asReversed()
            .forEach { stagedDelete ->
                try {
                    stagedDelete.rollback()
                } catch (exception: Exception) {
                    logger.warn("Failed to restore staged image deletion.", exception)
                }
            }
    }

    companion object {
        private const val MAX_GARDEN_BOOK_COUNT = 30L
        private val logger = LoggerFactory.getLogger(BookCommandService::class.java)
    }
}
