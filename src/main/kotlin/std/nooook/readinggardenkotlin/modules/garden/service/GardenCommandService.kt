package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
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
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class GardenCommandService(
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val gardenUserRepository: GardenUserRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun createGarden(
        userNo: Int,
        request: CreateGardenRequest,
    ): CreateGardenResponse {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")

        if (gardenUserRepository.countByUserNo(userNo) >= MAX_GARDEN_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 생성 개수 초과")
        }

        val garden = gardenRepository.save(
            std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity(
                gardenTitle = request.garden_title,
                gardenInfo = request.garden_info,
                gardenColor = request.garden_color,
            ),
        )

        gardenUserRepository.save(
            std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity(
                gardenNo = garden.gardenNo ?: throw IllegalStateException("Garden id was not generated"),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )

        return CreateGardenResponse(
            garden_no = garden.gardenNo ?: throw IllegalStateException("Garden id was not generated"),
            garden_title = request.garden_title,
            garden_info = request.garden_info,
            garden_color = request.garden_color,
        )
    }

    @Transactional
    fun updateGarden(
        userNo: Int,
        gardenNo: Int,
        request: UpdateGardenRequest,
    ): String {
        logger.debug("updateGarden: userNo={}, gardenNo={}, request={}", userNo, gardenNo, request)
        val garden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val membership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 수정 불가")

        if (!membership.gardenLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 수정 불가")
        }

        garden.gardenTitle = request.garden_title
        garden.gardenInfo = request.garden_info
        garden.gardenColor = request.garden_color
        gardenRepository.save(garden)
        return "가든 수정 성공"
    }

    @Transactional
    fun deleteGarden(
        userNo: Int,
        gardenNo: Int,
    ): String {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        val garden = gardenRepository.findByGardenNoForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val membership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        if (!membership.gardenLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }
        if (gardenUserRepository.countByGardenNo(gardenNo) > 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }
        if (gardenUserRepository.countByUserNo(userNo) <= 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 삭제 불가")
        }

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            deleteOwnedGardenResources(userNo, gardenNo, stagedDeletes)
            gardenUserRepository.delete(membership)
            gardenUserRepository.flush()
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
        userNo: Int,
        gardenNo: Int,
        toGardenNo: Int,
    ): String {
        lockGardensInDeterministicOrder(gardenNo, toGardenNo)

        val sourceGarden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val destinationGarden = gardenRepository.findById(toGardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 도착지 가든이 없습니다.") }
        if (gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo) == null ||
            gardenUserRepository.findByGardenNoAndUserNo(toGardenNo, userNo) == null
        ) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
        }

        val movableBookCount = bookRepository.countByUserNoAndGardenNo(userNo, gardenNo)
        val destinationBookCount = bookRepository.countByGardenNo(toGardenNo)
        if (destinationBookCount + movableBookCount > MAX_GARDEN_BOOK_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
        }

        bookRepository.findAllByUserNoAndGardenNo(userNo, gardenNo).forEach { book ->
            book.gardenNo = checkNotNull(destinationGarden.gardenNo)
            bookRepository.save(book)
        }
        return "가든 책 이동 성공"
    }

    @Transactional
    fun updateGardenMain(
        userNo: Int,
        gardenNo: Int,
    ): String {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")

        if (!gardenRepository.existsById(gardenNo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
        }

        val targetMembership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 메인 변경 불가")

        gardenUserRepository.findAllByUserNo(userNo)
            .filter { it.gardenMain && it.id != targetMembership.id }
            .forEach { membership ->
                membership.gardenMain = false
                gardenUserRepository.save(membership)
            }

        if (!targetMembership.gardenMain) {
            targetMembership.gardenMain = true
            gardenUserRepository.save(targetMembership)
        }

        return "가든 메인 변경 성공"
    }

    private fun lockGardensInDeterministicOrder(
        firstGardenNo: Int,
        secondGardenNo: Int,
    ) {
        listOf(firstGardenNo, secondGardenNo)
            .distinct()
            .sorted()
            .forEach { gardenNo ->
                val lockedGarden = gardenRepository.findByGardenNoForUpdate(gardenNo)
                    ?: throw if (gardenNo == secondGardenNo) {
                        ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 도착지 가든이 없습니다.")
                    } else {
                        ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
                    }
                checkNotNull(lockedGarden.gardenNo)
            }
    }

    private fun deleteOwnedGardenResources(
        userNo: Int,
        gardenNo: Int,
        stagedDeletes: MutableList<ImageStorage.StagedDelete>,
    ) {
        bookRepository.findAllByUserNoAndGardenNo(userNo, gardenNo).forEach { book ->
            val bookNo = checkNotNull(book.bookNo)
            bookReadRepository.deleteAllByBookNo(bookNo)

            bookImageRepository.findAllByBookNo(bookNo).forEach { image ->
                stagedDeletes += imageStorage.stageDelete(image.imageUrl)
                bookImageRepository.delete(image)
            }

            val memos = memoRepository.findAllByBookNo(bookNo)
            val memoIds = memos.mapNotNull { it.id }
            if (memoIds.isNotEmpty()) {
                memoImageRepository.findAllByMemoNoIn(memoIds).forEach { memoImage ->
                    stagedDeletes += imageStorage.stageDelete(memoImage.imageUrl)
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
