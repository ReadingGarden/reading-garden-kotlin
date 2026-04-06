package std.nooook.readinggardenkotlin.modules.memo.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.UpdateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class MemoCommandService(
    private val memoRepository: MemoRepository,
    private val bookRepository: BookRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun createMemo(
        userNo: Int,
        request: CreateMemoRequest,
    ): CreateMemoResponse {
        bookRepository.findByBookNoAndUserNo(request.book_no, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")

        val saved = memoRepository.save(
            MemoEntity(
                bookNo = request.book_no,
                memoContent = request.memo_content,
                userNo = userNo,
                memoLike = false,
            ),
        )

        return CreateMemoResponse(
            id = checkNotNull(saved.id) { "Memo id was not generated" },
        )
    }

    @Transactional
    fun updateMemo(
        userNo: Int,
        id: Int,
        request: UpdateMemoRequest,
    ): String {
        val memo = memoRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        bookRepository.findByBookNoAndUserNo(request.book_no, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")

        memo.bookNo = request.book_no
        memo.memoContent = request.memo_content
        memoRepository.save(memo)
        return "메모 수정 성공"
    }

    @Transactional
    fun deleteMemo(
        userNo: Int,
        id: Int,
    ): String {
        val memo = memoRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            memoImageRepository.findAllByMemoNoIn(listOf(id)).forEach { memoImage ->
                stagedDeletes += imageStorage.stageDelete(memoImage.imageUrl)
                memoImageRepository.delete(memoImage)
            }

            memoRepository.delete(memo)
            finalizeStagedDeletes(stagedDeletes)
            return "메모 삭제 성공"
        } catch (exception: Exception) {
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to delete memo resources.", exception)
            }
        }
    }

    @Transactional
    fun toggleMemoLike(
        userNo: Int,
        id: Int,
    ): String {
        val memo = memoRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        memo.memoLike = !memo.memoLike
        memoRepository.save(memo)
        return "메모 즐겨찾기 추가/해제"
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
                logger.warn("Failed to finalize staged memo image deletion.", exception)
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
                    logger.warn("Failed to restore staged memo image deletion.", exception)
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MemoCommandService::class.java)
    }
}
