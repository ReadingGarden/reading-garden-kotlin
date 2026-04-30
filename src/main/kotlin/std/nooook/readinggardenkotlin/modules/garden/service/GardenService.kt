package std.nooook.readinggardenkotlin.modules.garden.service

import org.springframework.transaction.annotation.Transactional
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
