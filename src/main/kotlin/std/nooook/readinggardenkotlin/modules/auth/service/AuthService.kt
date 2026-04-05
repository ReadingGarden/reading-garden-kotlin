package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.JwtException
import jakarta.transaction.Transactional
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
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushEntity
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val gardenRepository: GardenRepository,
    private val gardenUserRepository: GardenUserRepository,
    private val pushRepository: PushRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val bookImageRepository: BookImageRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val jwtService: LegacyJwtService,
    private val mailSender: MailSender,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun signup(request: CreateUserRequest): SignupResponse {
        validateSignupDuplicate(request)

        val user = userRepository.save(
            UserEntity(
                userEmail = request.user_email,
                userPassword = request.user_password.takeIf { it.isNotBlank() }?.let(::encodePassword).orEmpty(),
                userNick = generateRandomNick(),
                userImage = DEFAULT_USER_IMAGE,
                userFcm = request.user_fcm,
                userSocialId = request.user_social_id,
                userSocialType = request.user_social_type,
            ),
        )

        val userNo = user.userNo ?: throw IllegalStateException("User id was not generated")
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "${user.userNick}의 가든",
                gardenInfo = "독서가든에 오신걸 환영합니다☺️",
                gardenColor = "green",
            ),
        )

        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = garden.gardenNo ?: throw IllegalStateException("Garden id was not generated"),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )

        pushRepository.save(
            PushEntity(
                userNo = userNo,
                pushAppOk = true,
            ),
        )

        val tokenPair = persistRefreshToken(user)
        return SignupResponse(
            access_token = tokenPair["access_token"].orEmpty(),
            refresh_token = tokenPair["refresh_token"].orEmpty(),
            user_nick = user.userNick,
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
            userRepository.findByUserSocialIdAndUserSocialType(socialId, socialType)
                ?: throw badRequest("등록되지 않은 소셜입니다")
        } else {
            val foundUser = userRepository.findByUserEmail(email)
                ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
            if (!passwordEncoder.matches(password, foundUser.userPassword)) {
                throw badRequest("비밀번호가 일치하지 않습니다.")
            }
            foundUser
        }

        user.userFcm = fcmToken
        userRepository.save(user)
        return persistRefreshToken(user)
    }

    @Transactional
    fun logout(userNo: Int) {
        val user = requireUser(userNo)
        refreshTokenRepository.findByUserNo(userNo)?.let(refreshTokenRepository::delete)
        user.userFcm = ""
        userRepository.save(user)
    }

    @Transactional
    fun refresh(refreshToken: String): String =
        try {
            val claims = jwtService.parseRefreshToken(refreshToken)
            val userNo = (claims["user_no"] as? Number)?.toInt()
                ?: throw unauthorized("Unauthorized")
            val storedToken = refreshTokenRepository.findByUserNoAndToken(userNo, refreshToken)
                ?: throw unauthorized("Unauthorized")

            if (storedToken.exp?.isBefore(LocalDateTime.now()) == true) {
                refreshTokenRepository.delete(storedToken)
                throw unauthorized("Unauthorized")
            }

            val user = requireUser(userNo)
            jwtService.generateAccessToken(user)
        } catch (ex: JwtException) {
            throw unauthorized("Unauthorized")
        } catch (ex: IllegalArgumentException) {
            throw unauthorized("Unauthorized")
        }

    @Transactional
    fun deleteUser(userNo: Int) {
        val user = requireUser(userNo)
        val memberships = gardenUserRepository.findAllByUserNo(userNo)
        val books = bookRepository.findAllByUserNo(userNo)
        val memos = memoRepository.findAllByUserNo(userNo)

        memberships.forEach { membership ->
            if (membership.gardenLeader) {
                val members = gardenUserRepository.findAllByGardenNoOrderByGardenSignDateAsc(membership.gardenNo)
                if (members.size > 1) {
                    members.firstOrNull { it.userNo != userNo }?.let { nextLeader ->
                        nextLeader.gardenLeader = true
                        gardenUserRepository.save(nextLeader)
                    }
                } else {
                    gardenRepository.findById(membership.gardenNo).ifPresent(gardenRepository::delete)
                }
            }
            gardenUserRepository.delete(membership)
        }

        books.forEach { book ->
            val bookNo = book.bookNo ?: return@forEach
            bookReadRepository.deleteAllByBookNo(bookNo)
            bookImageRepository.deleteByBookNo(bookNo)
            bookRepository.delete(book)
        }

        memos.forEach { memo ->
            val memoNo = memo.id ?: return@forEach
            memoImageRepository.deleteByMemoNo(memoNo)
            memoRepository.delete(memo)
        }

        refreshTokenRepository.findByUserNo(userNo)?.let(refreshTokenRepository::delete)
        pushRepository.findByUserNo(userNo)?.let(pushRepository::delete)
        userRepository.delete(user)
    }

    @Transactional
    fun sendPasswordResetMail(email: String) {
        val user = userRepository.findByUserEmail(email)
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

        user.userAuthNumber = authNumber
        userRepository.save(user)
    }

    fun checkPasswordResetAuth(
        email: String,
        authNumber: String,
    ) {
        val user = userRepository.findByUserEmail(email)
            ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
        if (user.userAuthNumber != authNumber) {
            throw badRequest("인증번호 불일치")
        }
    }

    @Transactional
    fun updatePasswordWithoutToken(
        email: String,
        password: String,
    ) {
        val user = userRepository.findByUserEmail(email)
            ?: throw badRequest("등록되지 않은 이메일 주소입니다.")
        user.userPassword = encodePassword(password)
        userRepository.save(user)
    }

    @Transactional
    fun updatePassword(
        userNo: Int,
        password: String,
    ) {
        val user = requireUser(userNo)
        user.userPassword = encodePassword(password)
        userRepository.save(user)
    }

    fun getProfile(userNo: Int): UserProfileResponse {
        val user = requireUser(userNo)
        return UserProfileResponse(
            user_no = user.userNo ?: userNo,
            user_nick = user.userNick,
            user_email = user.userEmail,
            user_social_type = user.userSocialType,
            user_image = user.userImage,
            user_created_at = user.userCreatedAt,
            garden_count = gardenUserRepository.countByUserNo(userNo),
            read_book_count = bookRepository.countByUserNoAndBookStatus(userNo, READ_BOOK_STATUS),
            like_book_count = bookRepository.countByUserNoAndBookStatus(userNo, LIKE_BOOK_STATUS),
        )
    }

    @Transactional
    fun updateProfile(
        userNo: Int,
        userNick: String?,
        userImage: String?,
    ): UserSummaryResponse {
        val user = requireUser(userNo)
        if (!userNick.isNullOrBlank()) {
            user.userNick = userNick
        } else if (!userImage.isNullOrBlank()) {
            user.userImage = userImage
        }

        val savedUser = userRepository.save(user)
        return UserSummaryResponse(
            user_no = savedUser.userNo ?: userNo,
            user_nick = savedUser.userNick,
            user_email = savedUser.userEmail,
            user_image = savedUser.userImage,
            user_fcm = savedUser.userFcm,
            user_social_id = savedUser.userSocialId,
            user_social_type = savedUser.userSocialType,
            user_created_at = savedUser.userCreatedAt,
        )
    }

    private fun validateSignupDuplicate(request: CreateUserRequest) {
        if (request.user_social_id.isNotBlank()) {
            if (userRepository.existsByUserSocialIdAndUserSocialType(request.user_social_id, request.user_social_type)) {
                throw conflict("소셜 아이디 중복")
            }
        } else if (userRepository.existsByUserEmail(request.user_email)) {
            throw conflict("이메일 중복")
        }
    }

    private fun persistRefreshToken(user: UserEntity): Map<String, String> {
        val userNo = user.userNo ?: throw IllegalStateException("User id is required")
        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateRefreshToken(user)

        refreshTokenRepository.findByUserNo(userNo)?.let(refreshTokenRepository::delete)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                userNo = userNo,
                token = refreshToken,
                exp = LocalDateTime.ofInstant(jwtService.refreshTokenExpiry(), KST_ZONE_ID),
            ),
        )

        return mapOf(
            "access_token" to accessToken,
            "refresh_token" to refreshToken,
        )
    }

    private fun requireUser(userNo: Int): UserEntity =
        userRepository.findByUserNo(userNo)
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
        private val KST_ZONE_ID = java.time.ZoneId.of("Asia/Seoul")
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
