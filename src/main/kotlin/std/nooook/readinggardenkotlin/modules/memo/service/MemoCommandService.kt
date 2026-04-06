package std.nooook.readinggardenkotlin.modules.memo.service

import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.UpdateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class MemoCommandService(
    private val memoRepository: MemoRepository,
    private val bookRepository: BookRepository,
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

        memoRepository.delete(memo)
        return "메모 삭제 성공"
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
}
