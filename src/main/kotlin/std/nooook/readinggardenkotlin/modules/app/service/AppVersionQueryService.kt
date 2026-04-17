package std.nooook.readinggardenkotlin.modules.app.service

import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.app.controller.AppVersionResponse
import std.nooook.readinggardenkotlin.modules.app.repository.AppVersionRepository

@Service
@Transactional
class AppVersionQueryService(
    private val appVersionRepository: AppVersionRepository,
) {
    fun getByPlatform(platform: String): AppVersionResponse {
        if (platform !in ALLOWED_PLATFORMS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "platform must be one of: ${ALLOWED_PLATFORMS.joinToString()}",
            )
        }

        val entity = appVersionRepository.findByPlatform(platform)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "App version not found for platform: $platform",
            )

        return AppVersionResponse(
            platform = entity.platform,
            latest_version = entity.latestVersion,
            min_supported_version = entity.minSupportedVersion,
            store_url = entity.storeUrl,
        )
    }

    companion object {
        private val ALLOWED_PLATFORMS = setOf("ios", "android")
    }
}
