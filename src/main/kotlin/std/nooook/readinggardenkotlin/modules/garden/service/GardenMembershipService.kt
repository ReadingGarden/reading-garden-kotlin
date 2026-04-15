package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.service.GardenMemberJoinedPushEvent

@Service
class GardenMembershipService(
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val gardenMemberRepository: GardenMemberRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun leaveGardenMember(
        userId: Long,
        gardenNo: Long,
    ): String {
        userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        gardenRepository.findByIdForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val membership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
        if (gardenMemberRepository.countByGardenId(gardenNo) <= 1) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
        }

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            deleteOwnedGardenResources(userId, gardenNo, stagedDeletes)

            if (membership.isLeader) {
                val nextLeader = gardenMemberRepository.findAllByGardenIdOrderByJoinDateAsc(gardenNo)
                    .firstOrNull { it.user.id != userId }
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 탈퇴 불가")
                if (!nextLeader.isLeader) {
                    nextLeader.isLeader = true
                    gardenMemberRepository.save(nextLeader)
                }
            }

            gardenMemberRepository.delete(membership)
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
        userId: Long,
        gardenNo: Long,
        targetUserId: Long,
    ): String {
        userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        gardenRepository.findByIdForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        val currentLeaderMembership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 변경 불가")
        if (!currentLeaderMembership.isLeader) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 변경 불가")
        }

        val targetMembership = gardenMemberRepository.findByGardenIdAndUserId(gardenNo, targetUserId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든 멤버가 없습니다.")

        if (currentLeaderMembership.id != targetMembership.id) {
            currentLeaderMembership.isLeader = false
            targetMembership.isLeader = true
            gardenMemberRepository.save(currentLeaderMembership)
            gardenMemberRepository.save(targetMembership)
        }

        return "가든 멤버 변경 성공"
    }

    @Transactional
    fun inviteGardenMember(
        userId: Long,
        gardenNo: Long,
    ): String {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
        val garden = gardenRepository.findByIdForUpdate(gardenNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")

        if (gardenMemberRepository.existsByGardenIdAndUserId(gardenNo, userId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 가든")
        }
        if (gardenMemberRepository.countByGardenId(gardenNo) >= MAX_GARDEN_MEMBER_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 멤버 초과")
        }

        val recipientUserIds = gardenMemberRepository.findAllByGardenIdOrderByJoinDateAsc(gardenNo)
            .asSequence()
            .map { it.user.id }
            .filter { it != userId }
            .toList()

        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = false,
                isMain = false,
            ),
        )

        applicationEventPublisher.publishEvent(
            GardenMemberJoinedPushEvent(
                gardenNo = gardenNo,
                recipientUserIds = recipientUserIds,
            ),
        )

        return "가든 초대 완료"
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
}
