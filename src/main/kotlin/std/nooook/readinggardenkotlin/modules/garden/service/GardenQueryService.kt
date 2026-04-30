package std.nooook.readinggardenkotlin.modules.garden.service

import org.springframework.transaction.annotation.Transactional
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
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository

interface GardenQueryService {
    fun getGardenList(userId: Long): List<GardenListItemResponse>

    fun getGardenDetail(userId: Long, gardenNo: Long): GardenDetailResponse
}

@Service
class DefaultGardenQueryService(
    private val gardenRepository: GardenRepository,
    private val gardenMemberRepository: GardenMemberRepository,
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val userRepository: UserRepository,
) : GardenQueryService {
    @Transactional(readOnly = true)
    override fun getGardenList(userId: Long): List<GardenListItemResponse> =
        gardenMemberRepository.findAllByUserIdOrderByIsMainDescJoinDateAsc(userId)
            .map { membership ->
                val garden = gardenRepository.findById(membership.garden.id)
                    .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
                GardenListItemResponse(
                    garden_no = garden.id,
                    garden_title = garden.title,
                    garden_info = garden.info,
                    garden_color = garden.color,
                    garden_members = gardenMemberRepository.countByGardenId(membership.garden.id).toInt(),
                    book_count = bookRepository.countByGardenId(membership.garden.id).toInt(),
                    garden_created_at = formatDateTime(garden.createdAt),
                )
            }

    @Transactional(readOnly = true)
    override fun getGardenDetail(userId: Long, gardenNo: Long): GardenDetailResponse {
        val garden = gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.") }
        val members = gardenMemberRepository.findAllByGardenIdOrderByIsLeaderDescJoinDateAsc(gardenNo)
        val usersByNo = userRepository.findAllByIdIn(members.map { it.user.id })
            .associateBy { it.id }

        return GardenDetailResponse(
            garden_no = garden.id,
            garden_title = garden.title,
            garden_info = garden.info,
            garden_color = garden.color,
            garden_created_at = formatDateTime(garden.createdAt),
            book_list = bookRepository.findAllByGardenIdOrderByIdAsc(gardenNo)
                .map { book ->
                    val latestRead = bookReadRepository.findTopByBookIdOrderByCreatedAtDesc(book.id)
                    GardenDetailBookResponse(
                        book_no = book.id,
                        book_isbn = book.isbn,
                        book_title = book.title,
                        book_author = book.author,
                        book_publisher = book.publisher,
                        book_info = book.info,
                        book_image_url = book.imageUrl,
                        book_tree = book.tree,
                        book_status = book.status,
                        percent = latestRead?.let { calculatePercent(it.currentPage, book.page) } ?: 0.0,
                        user_no = book.user.id,
                        book_page = book.page,
                    )
                },
            garden_members = members.map { member ->
                val user = usersByNo[member.user.id]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")
                GardenListMemberResponse(
                    user_no = user.id,
                    user_nick = user.nick,
                    user_image = user.image,
                    garden_leader = member.isLeader,
                    garden_sign_date = formatDateTime(member.joinDate),
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
