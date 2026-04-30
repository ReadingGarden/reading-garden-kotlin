package std.nooook.readinggardenkotlin.modules.garden.service

import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.UpdateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class GardenCommandService(
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val gardenMemberRepository: GardenMemberRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun createGarden(
        userId: Long,
        request: CreateGardenRequest,
    ): CreateGardenResponse {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")

        if (gardenMemberRepository.countByUserId(userId) >= MAX_GARDEN_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 생성 개수 초과")
        }

        val garden = gardenRepository.save(
            GardenEntity(
                title = request.garden_title,
                info = request.garden_info,
                color = request.garden_color,
            ),
        )

        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = true,
                isMain = true,
            ),
        )

        return CreateGardenResponse(
            garden_no = garden.id,
            garden_title = request.garden_title,
            garden_info = request.garden_info,
            garden_color = request.garden_color,
        )
    }

    @Transactional
    fun updateGarden(
        userId: Long,
        gardenNo: Long,
        request: UpdateGardenRequest,
    ): String {
        logger.debug("updateGarden: userId={}, gardenNo={}, request={}", userId, gardenNo, request)
        val garden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val membership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 수정 불가")

        if (!membership.isLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 수정 불가")
        }

        garden.title = request.garden_title
        garden.info = request.garden_info
        garden.color = request.garden_color
        gardenRepository.save(garden)
        return "가든 수정 성공"
    }

    @Transactional
    fun deleteGarden(
        userId: Long,
        gardenNo: Long,
    ): String {
        userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        val garden = gardenRepository.findByIdForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val membership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        if (!membership.isLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }
        if (gardenMemberRepository.countByGardenId(gardenNo) > 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }
        if (gardenMemberRepository.countByUserId(userId) <= 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            deleteOwnedGardenResources(userId, gardenNo, stagedDeletes)
            gardenMemberRepository.delete(membership)
            gardenMemberRepository.flush()
            gardenRepository.delete(garden)
            finalizeStagedDeletes(stagedDeletes)
            return "가든 삭제 성공"
        } catch (exception: Exception) {
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to delete garden resources.", exception)
            }
        }
    }

    @Transactional
    fun moveGardenBook(
        userId: Long,
        gardenNo: Long,
        toGardenNo: Long,
    ): String {
        lockGardensInDeterministicOrder(gardenNo, toGardenNo)

        val sourceGarden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val destinationGarden = gardenRepository.findById(toGardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 도착지 가든이 없습니다.") }
        if (gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId) == null ||
            gardenMemberRepository.findByGardenIdAndUserId(toGardenNo, userId) == null
        ) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
        }

        val movableBookCount = bookRepository.countByUserIdAndGardenId(userId, gardenNo)
        val destinationBookCount = bookRepository.countByGardenId(toGardenNo)
        if (destinationBookCount + movableBookCount > MAX_GARDEN_BOOK_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
        }

        bookRepository.findAllByUserIdAndGardenId(userId, gardenNo).forEach { book ->
            book.garden = destinationGarden
            bookRepository.save(book)
        }
        return "가든 책 이동 성공"
    }

    @Transactional
    fun updateGardenMain(
        userId: Long,
        gardenNo: Long,
    ): String {
        userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")

        if (!gardenRepository.existsById(gardenNo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
        }

        val targetMembership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 메인 변경 불가")

        gardenMemberRepository.findAllByUserId(userId)
            .filter { it.isMain && it.id != targetMembership.id }
            .forEach { membership ->
                membership.isMain = false
                gardenMemberRepository.save(membership)
            }

        if (!targetMembership.isMain) {
            targetMembership.isMain = true
            gardenMemberRepository.save(targetMembership)
        }

        return "가든 메인 변경 성공"
    }

    private fun lockGardensInDeterministicOrder(
        firstGardenNo: Long,
        secondGardenNo: Long,
    ) {
        listOf(firstGardenNo, secondGardenNo)
            .distinct()
            .sorted()
            .forEach { gardenNo ->
                val lockedGarden = gardenRepository.findByIdForUpdate(gardenNo)
                    ?: throw if (gardenNo == secondGardenNo) {
                        ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 도착지 가든이 없습니다.")
                    } else {
                        ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
                    }
            }
    }

    private fun deleteOwnedGardenResources(
        userId: Long,
        gardenNo: Long,
        stagedDeletes: MutableList<ImageStorage.StagedDelete>,
    ) {
        bookRepository.findAllByUserIdAndGardenId(userId, gardenNo).forEach { book ->
            bookReadRepository.deleteAllByBookId(book.id)

            bookImageRepository.findAllByBookId(book.id).forEach { image ->
                stagedDeletes += imageStorage.stageDelete(image.url)
                bookImageRepository.delete(image)
            }

            val memos = memoRepository.findAllByBookId(book.id)
            val memoIds = memos.map { it.id }
            if (memoIds.isNotEmpty()) {
                memoImageRepository.findAllByMemoIdIn(memoIds).forEach { memoImage ->
                    stagedDeletes += imageStorage.stageDelete(memoImage.url)
                    memoImageRepository.delete(memoImage)
                }
            }
            memos.forEach { memoRepository.delete(it) }
            bookRepository.delete(book)
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
                logger.warn("Failed to finalize staged garden deletion.", exception)
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
                    logger.warn("Failed to restore staged garden deletion.", exception)
                }
            }
    }

    companion object {
        private const val MAX_GARDEN_COUNT = 5L
        private const val MAX_GARDEN_BOOK_COUNT = 30L
        private val logger = LoggerFactory.getLogger(GardenCommandService::class.java)
    }
}
