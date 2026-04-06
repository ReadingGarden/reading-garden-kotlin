package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenDetailResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenDetailBookResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenListItemResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenListMemberResponse
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository

interface GardenQueryService {
    fun getGardenList(userNo: Int): List<GardenListItemResponse>

    fun getGardenDetail(userNo: Int, gardenNo: Int): GardenDetailResponse
}

@Service
class DefaultGardenQueryService(
    private val gardenRepository: GardenRepository,
    private val gardenUserRepository: GardenUserRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val userRepository: UserRepository,
) : GardenQueryService {
    @Transactional
    override fun getGardenList(userNo: Int): List<GardenListItemResponse> =
        gardenUserRepository.findAllByUserNoOrderByGardenMainDescGardenSignDateAsc(userNo)
            .map { membership ->
                val garden = gardenRepository.findById(membership.gardenNo)
                    .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
                GardenListItemResponse(
                    garden_no = checkNotNull(garden.gardenNo),
                    garden_title = garden.gardenTitle,
                    garden_info = garden.gardenInfo,
                    garden_color = garden.gardenColor,
                    garden_members = gardenUserRepository.countByGardenNo(membership.gardenNo).toInt(),
                    book_count = bookRepository.countByGardenNo(membership.gardenNo).toInt(),
                    garden_created_at = formatDateTime(garden.gardenCreatedAt),
                )
            }

    @Transactional
    override fun getGardenDetail(userNo: Int, gardenNo: Int): GardenDetailResponse {
        if (!gardenUserRepository.existsByGardenNoAndUserNo(gardenNo, userNo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
        }

        val garden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val members = gardenUserRepository.findAllByGardenNoOrderByGardenLeaderDescGardenSignDateAsc(gardenNo)
        val usersByNo = userRepository.findAllByUserNoIn(members.map { it.userNo })
            .associateBy { checkNotNull(it.userNo) }

        return GardenDetailResponse(
            garden_no = checkNotNull(garden.gardenNo),
            garden_title = garden.gardenTitle,
            garden_info = garden.gardenInfo,
            garden_color = garden.gardenColor,
            garden_created_at = formatDateTime(garden.gardenCreatedAt),
            book_list = bookRepository.findAllByGardenNoOrderByBookNoAsc(gardenNo)
                .map { book ->
                    val bookNo = checkNotNull(book.bookNo)
                    val latestRead = bookReadRepository.findTopByBookNoOrderByCreatedAtDesc(bookNo)
                    GardenDetailBookResponse(
                        book_no = bookNo,
                        book_isbn = book.bookIsbn,
                        book_title = book.bookTitle,
                        book_author = book.bookAuthor,
                        book_publisher = book.bookPublisher,
                        book_info = book.bookInfo,
                        book_image_url = book.bookImageUrl,
                        book_tree = book.bookTree,
                        book_status = book.bookStatus,
                        percent = latestRead?.let { calculatePercent(it.bookCurrentPage, book.bookPage) } ?: 0.0,
                        user_no = book.userNo,
                        book_page = book.bookPage,
                    )
                },
            garden_members = members.map { member ->
                val user = usersByNo[member.userNo]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
                GardenListMemberResponse(
                    user_no = checkNotNull(user.userNo),
                    user_nick = user.userNick,
                    user_image = user.userImage,
                    garden_leader = member.gardenLeader,
                    garden_sign_date = formatDateTime(member.gardenSignDate),
                )
            },
        )
    }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(LEGACY_DATE_TIME_FORMATTER)

    private fun calculatePercent(bookCurrentPage: Int, bookPage: Int): Double {
        if (bookPage <= 0) {
            return 0.0
        }
        return bookCurrentPage.toDouble() / bookPage * 100
    }

    companion object {
        private val LEGACY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
