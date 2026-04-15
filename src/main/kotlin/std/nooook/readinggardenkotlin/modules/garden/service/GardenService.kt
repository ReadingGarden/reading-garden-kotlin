package std.nooook.readinggardenkotlin.modules.garden.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenResponse

@Service
class GardenService(
    private val gardenCommandService: GardenCommandService,
) {
    @Transactional
    fun createGarden(
        userId: Long,
        request: CreateGardenRequest,
    ): CreateGardenResponse = gardenCommandService.createGarden(userId, request)
}
