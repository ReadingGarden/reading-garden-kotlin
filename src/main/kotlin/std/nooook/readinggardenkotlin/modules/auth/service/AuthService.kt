package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.JwtException
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.auth.controller.CreateUserRequest
import std.nooook.readinggardenkotlin.modules.auth.controller.SignupResponse
import std.nooook.readinggardenkotlin.modules.auth.controller.UserProfileResponse
import std.nooook.readinggardenkotlin.modules.auth.controller.UserSummaryResponse
import std.nooook.readinggardenkotlin.modules.auth.entity.RefreshTokenEntity
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.integration.MailSender
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import std.nooook.readinggardenkotlin.modules.scheduler.service.AuthPasswordResetExpiryJobService
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val gardenRepository: GardenRepository,
    private val gardenMemberRepository: GardenMemberRepository,
    private val pushSettingsRepository: PushSettingsRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val jwtService: LegacyJwtService,
    private val mailSender: MailSender,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetExpiryJobService: AuthPasswordResetExpiryJobService,
) {
    @Transactional
    fun signup(request: CreateUserRequest): SignupResponse {
        validateSignupDuplicate(request)

        val user = userRepository.save(
            UserEntity(
                email = request.user_email,
                password = request.user_password.takeIf { it.isNotBlank() }?.let(::encodePassword).orEmpty(),
                nick = generateRandomNick(),
                image = DEFAULT_USER_IMAGE,
                fcm = request.user_fcm,
                socialId = request.user_social_id,
                socialType = request.user_social_type,
            ),
        )

        val garden = gardenRepository.save(
            GardenEntity(
                title = "${user.nick}의 가든",
                info = "독서가든에 오신걸 환영합니다☺️",
                color = "green",
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

        pushSettingsRepository.save(
            PushSettingsEntity(
                user = user,
                appOk = true,
            ),
        )

        val tokenPair = persistRefreshToken(user)
        return SignupResponse(
            access_token = tokenPair["access_token"].orEmpty(),
            refresh_token = tokenPair["refresh_token"].orEmpty(),
            user_nick = user.nick,
        )
    }

    @Transactional
    fun login(
        email: String,
        password: String,
        fcmToken: String,
    ): Map<String, String> = login(email, password, fcmToken, "", "")

    @Transactional
    fun login(
        email: String,
        password: String,
        fcmToken: String,
        socialId: String,
        socialType: String,
    ): Map<String, String> {
        val user = if (socialId.isNotBlank()) {
            userRepository.findBySocialIdAndSocialType(socialId, socialType)
                ?: throw badRequest("등록되지 않은 소셜입니다")
        } else {
            val foundUser = userRepository.findByEmail(email)
                ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
            if (!passwordEncoder.matches(password, foundUser.password)) {
                throw badRequest("비밀번호가 일치하지 않습니다.")
            }
            foundUser
        }

        user.fcm = fcmToken
        userRepository.save(user)
        return persistRefreshToken(user)
    }

    @Transactional
    fun logout(userId: Long) {
        val user = requireUser(userId)
        refreshTokenRepository.deleteAllByUserId(userId)
        user.fcm = ""
        userRepository.save(user)
    }

    @Transactional
    fun refresh(refreshToken: String): String =
        try {
            val claims = jwtService.parseRefreshToken(refreshToken)
            val userId = (claims["user_no"] as? Number)?.toLong()
                ?: throw unauthorized("Unauthorized")
            val storedToken = refreshTokenRepository.findByUserIdAndToken(userId, refreshToken)
                ?: throw unauthorized("Unauthorized")

            if (storedToken.exp?.isBefore(LocalDateTime.now(UTC_ZONE_OFFSET)) == true) {
                refreshTokenRepository.delete(storedToken)
                throw unauthorized("Unauthorized")
            }

            val user = requireUser(userId)
            jwtService.generateAccessToken(user)
        } catch (ex: JwtException) {
            throw unauthorized("Unauthorized")
        } catch (ex: IllegalArgumentException) {
            throw unauthorized("Unauthorized")
        }

    @Transactional
    fun deleteUser(userId: Long) {
        val user = requireUser(userId)
        val memberships = gardenMemberRepository.findAllByUserId(userId)
        val books = bookRepository.findAllByUserId(userId)
        val memos = memoRepository.findAllByUserId(userId)

        memberships.forEach { membership ->
            if (membership.isLeader) {
                val members = gardenMemberRepository.findAllByGardenIdOrderByJoinDateAsc(membership.garden.id)
                if (members.size > 1) {
                    members.firstOrNull { it.user.id != userId }?.let { nextLeader ->
                        nextLeader.isLeader = true
                        gardenMemberRepository.save(nextLeader)
                    }
                }
            }
            gardenMemberRepository.delete(membership)
        }

        memos.forEach { memo ->
            memoImageRepository.deleteByMemoId(memo.id)
            memoRepository.delete(memo)
        }

        books.forEach { book ->
            bookReadRepository.deleteAllByBookId(book.id)
            bookImageRepository.deleteByBookId(book.id)
            bookRepository.delete(book)
        }

        refreshTokenRepository.deleteAllByUserId(userId)
        pushSettingsRepository.findByUserId(userId)?.let(pushSettingsRepository::delete)
        userRepository.delete(user)
    }

    @Transactional
    fun sendPasswordResetMail(email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
        val authNumber = generateRandomString(5)

        try {
            mailSender.send(
                email = email,
                title = "[독서가든] 인증번호 안내드립니다",
                content = authNumber,
            )
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "메일 전송 실패", ex)
        }

        user.authNumber = authNumber
        userRepository.save(user)
        passwordResetExpiryJobService.schedulePasswordResetExpiry(
            user.id,
            authNumber,
        )
    }

    @Transactional(noRollbackFor = [ResponseStatusException::class])
    fun checkPasswordResetAuth(
        email: String,
        authNumber: String,
    ) {
        val user = userRepository.findByEmail(email)
            ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
        if (user.authNumber != authNumber || !passwordResetExpiryJobService.isPasswordResetAuthValid(user.id, authNumber)) {
            throw badRequest("인증번호 불일치")
        }
    }

    @Transactional
    fun updatePasswordWithoutToken(
        email: String,
        password: String,
    ) {
        val user = userRepository.findByEmail(email)
            ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
        user.password = encodePassword(password)
        userRepository.save(user)
    }

    @Transactional
    fun updatePassword(
        userId: Long,
        password: String,
    ) {
        val user = requireUser(userId)
        user.password = encodePassword(password)
        userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun getProfile(userId: Long): UserProfileResponse {
        val user = requireUser(userId)
        return UserProfileResponse(
            user_no = user.id,
            user_nick = user.nick,
            user_email = user.email,
            user_social_type = user.socialType,
            user_image = user.image,
            user_created_at = user.createdAt,
            garden_count = gardenMemberRepository.countByUserId(userId),
            read_book_count = bookRepository.countByUserIdAndStatus(userId, READ_BOOK_STATUS),
            like_book_count = bookRepository.countByUserIdAndStatus(userId, LIKE_BOOK_STATUS),
        )
    }

    @Transactional
    fun updateProfile(
        userId: Long,
        userNick: String?,
        userImage: String?,
        userFcm: String? = null,
    ): UserSummaryResponse {
        val user = requireUser(userId)
        if (!userNick.isNullOrBlank()) {
            user.nick = userNick
        } else if (!userImage.isNullOrBlank()) {
            user.image = userImage
        }
        if (userFcm != null) {
            user.fcm = userFcm
        }

        val savedUser = userRepository.save(user)
        return UserSummaryResponse(
            user_no = savedUser.id,
            user_nick = savedUser.nick,
            user_email = savedUser.email,
            user_image = savedUser.image,
            user_fcm = savedUser.fcm,
            user_social_id = savedUser.socialId,
            user_social_type = savedUser.socialType,
            user_created_at = savedUser.createdAt,
        )
    }

    private fun validateSignupDuplicate(request: CreateUserRequest) {
        if (request.user_social_id.isNotBlank()) {
            if (userRepository.existsBySocialIdAndSocialType(request.user_social_id, request.user_social_type)) {
                throw conflict("소셜 아이디 중복")
            }
        } else if (userRepository.existsByEmail(request.user_email)) {
            throw conflict("이메일 중복")
        }
    }

    private fun persistRefreshToken(user: UserEntity): Map<String, String> {
        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateRefreshToken(user)

        refreshTokenRepository.deleteAllByUserId(user.id)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                user = user,
                token = refreshToken,
                exp = LocalDateTime.ofInstant(jwtService.refreshTokenExpiry(), UTC_ZONE_OFFSET),
            ),
        )

        return mapOf(
            "access_token" to accessToken,
            "refresh_token" to refreshToken,
        )
    }

    private fun requireUser(userId: Long): UserEntity =
        userRepository.findById(userId).orElse(null)
            ?: throw badRequest("일치하는 사용자 정보가 없습니다.")

    private fun badRequest(message: String) =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun conflict(message: String) =
        ResponseStatusException(HttpStatus.CONFLICT, message)

    private fun unauthorized(message: String) =
        ResponseStatusException(HttpStatus.UNAUTHORIZED, message)

    private fun generateRandomString(length: Int): String =
        buildString(length) {
            repeat(length) {
                append(RANDOM_STRING_SOURCE.random())
            }
        }

    private fun generateRandomNick(): String = RANDOM_NICK_FIRST.random() + RANDOM_NICK_SECOND.random()

    private fun encodePassword(password: String): String =
        passwordEncoder.encode(password) ?: throw IllegalStateException("Encoded password must not be null")

    companion object {
        private const val DEFAULT_USER_IMAGE = "데이지"
        private const val READ_BOOK_STATUS = 1
        private const val LIKE_BOOK_STATUS = 2
        private val UTC_ZONE_OFFSET = ZoneOffset.UTC
        private const val RANDOM_STRING_SOURCE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val RANDOM_NICK_FIRST = listOf(
            "눈부신", "따뜻한", "우수한", "은밀한", "침착한",
            "잠든", "풍부한", "환상적인", "고요한", "느긋한",
            "독특한", "위대한", "미묘한", "섬세한", "즐거운",
            "행복한", "고독한", "신비로운", "찬란한", "조용한",
            "빛나는", "화려한", "평화로운", "우아한", "뜨거운",
            "차가운", "부드러운", "귀여운", "발랄한", "활발한",
        )
        private val RANDOM_NICK_SECOND = listOf(
            "얼룩말", "양", "낙타", "사막여우", "기린",
            "코끼리", "하마", "코알라", "나무늘보", "호랑이",
            "사자", "부엉이", "고래", "상어", "개구리",
            "구피", "고양이", "강아지", "햄스터", "카피바라",
            "쿼카", "판다", "거북이", "토끼", "불가사리",
            "해파리", "미어캣", "도마뱀", "기니피그", "사슴",
        )
    }
}
