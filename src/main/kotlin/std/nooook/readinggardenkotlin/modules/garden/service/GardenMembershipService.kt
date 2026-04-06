package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushService

@Service
class GardenMembershipService(
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val gardenUserRepository: GardenUserRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
    private val pushService: PushService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun leaveGardenMember(
        userNo: Int,
        gardenNo: Int,
    ): String {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        gardenRepository.findByGardenNoForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val membership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
        if (gardenUserRepository.countByGardenNo(gardenNo) <= 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
        }

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            deleteOwnedGardenResources(userNo, gardenNo, stagedDeletes)

            if (membership.gardenLeader) {
                val nextLeader = gardenUserRepository.findAllByGardenNoOrderByGardenSignDateAsc(gardenNo)
                    .firstOrNull { it.userNo != userNo }
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
                if (!nextLeader.gardenLeader) {
                    nextLeader.gardenLeader = true
                    gardenUserRepository.save(nextLeader)
                }
            }

            gardenUserRepository.delete(membership)
            finalizeStagedDeletes(stagedDeletes)
            return "가든 탈퇴 성공"
        } catch (exception: Exception) {
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to leave garden resources.", exception)
            }
        }
    }

    @Transactional
    fun updateGardenMember(
        userNo: Int,
        gardenNo: Int,
        targetUserNo: Int,
    ): String {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        gardenRepository.findByGardenNoForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val currentLeaderMembership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 변경 불가")
        if (!currentLeaderMembership.gardenLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 변경 불가")
        }

        val targetMembership = gardenUserRepository.findByGardenNoAndUserNo(gardenNo, targetUserNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든 멤버가 없습니다.")

        if (currentLeaderMembership.id != targetMembership.id) {
            currentLeaderMembership.gardenLeader = false
            targetMembership.gardenLeader = true
            gardenUserRepository.save(currentLeaderMembership)
            gardenUserRepository.save(targetMembership)
        }

        return "가든 멤버 변경 성공"
    }

    @Transactional
    fun inviteGardenMember(
        userNo: Int,
        gardenNo: Int,
    ): String {
        userRepository.findByUserNoForUpdate(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        gardenRepository.findByGardenNoForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        if (gardenUserRepository.existsByGardenNoAndUserNo(gardenNo, userNo)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 가든")
        }
        if (gardenUserRepository.countByGardenNo(gardenNo) >= MAX_GARDEN_MEMBER_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 초과")
        }

        val recipientUserNos = gardenUserRepository.findAllByGardenNoOrderByGardenSignDateAsc(gardenNo)
            .asSequence()
            .map { it.userNo }
            .filter { it != userNo }
            .toList()

        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        applicationEventPublisher.publishEvent(
            GardenMemberJoinedEvent(
                gardenNo = gardenNo,
                recipientUserNos = recipientUserNos,
            ),
        )

        return "가든 초대 완료"
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGardenMemberJoined(event: GardenMemberJoinedEvent) {
        event.recipientUserNos.forEach { userNo ->
            pushService.sendNewMemberPush(userNo, event.gardenNo)
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
                logger.warn("Failed to finalize staged garden member deletion.", exception)
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
                    logger.warn("Failed to restore staged garden member deletion.", exception)
                }
            }
    }

    companion object {
        private const val MAX_GARDEN_MEMBER_COUNT = 10L
        private val logger = LoggerFactory.getLogger(GardenMembershipService::class.java)
    }

    data class GardenMemberJoinedEvent(
        val gardenNo: Int,
        val recipientUserNos: List<Int>,
    )
}
