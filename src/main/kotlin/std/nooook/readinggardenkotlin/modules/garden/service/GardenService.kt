package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenResponse
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository

@Service
class GardenService(
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val gardenUserRepository: GardenUserRepository,
) {
    @Transactional
    fun createGarden(
        userNo: Int,
        request: CreateGardenRequest,
    ): CreateGardenResponse {
        userRepository.findByUserNo(userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 사용자 정보가 없습니다.")

        if (gardenUserRepository.countByUserNo(userNo) >= MAX_GARDEN_COUNT) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 생성 개수 초과")
        }

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = request.garden_title,
                gardenInfo = request.garden_info,
                gardenColor = request.garden_color,
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

        return CreateGardenResponse(
            garden_no = garden.gardenNo ?: throw IllegalStateException("Garden id was not generated"),
            garden_title = request.garden_title,
            garden_info = request.garden_info,
            garden_color = request.garden_color,
        )
    }

    companion object {
        private const val MAX_GARDEN_COUNT = 5L
    }
}
