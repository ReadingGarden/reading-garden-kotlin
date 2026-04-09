package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class FcmClientConfig {
    @Bean
    @ConditionalOnMissingBean(FcmClient::class)
    fun fcmClient(
        @Value("\${app.firebase.project-id:}") projectId: String,
        @Value("\${app.firebase.service-account-file:}") serviceAccountFile: String,
    ): FcmClient {
        if (!hasUsableFirebaseConfig(projectId, serviceAccountFile)) {
            logger.info(
                "Firebase FCM client is disabled. Falling back to no-op client. projectIdPresent={}, serviceAccountUsable={}",
                projectId.isNotBlank(),
                hasUsableServiceAccountPath(serviceAccountFile),
            )
            return NoopFcmClient()
        }

        val firebaseAppName = "reading-garden-${UUID.randomUUID()}"

        return try {
            val firebaseApp = createFirebaseApp(firebaseAppName, projectId, serviceAccountFile)
            FirebaseAdminFcmClient(
                firebaseApp = firebaseApp,
                firebaseMessagingSender = FirebaseAdminMessagingSender(FirebaseMessaging.getInstance(firebaseApp)),
            )
        } catch (exception: Exception) {
            logger.error(
                "Firebase FCM client initialization failed. Falling back to no-op client. projectId={}, serviceAccountFile={}",
                projectId.trim(),
                serviceAccountFile.trim(),
                exception,
            )
            NoopFcmClient()
        }
    }

    @Bean("pushClock")
    fun pushClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    private fun createFirebaseApp(
        appName: String,
        projectId: String,
        serviceAccountFile: String,
    ): FirebaseApp {
        val serviceAccountPath = requireNotNull(serviceAccountPath(serviceAccountFile))
        Files.newInputStream(serviceAccountPath).use { inputStream ->
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(inputStream))
                .setProjectId(projectId.trim())
                .build()
            return FirebaseApp.initializeApp(options, appName)
        }
    }

    private fun hasUsableFirebaseConfig(
        projectId: String,
        serviceAccountFile: String,
    ): Boolean = projectId.isNotBlank() && hasUsableServiceAccountPath(serviceAccountFile)

    private fun isReadableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isReadable(path)

    private fun hasUsableServiceAccountPath(serviceAccountFile: String): Boolean = try {
        serviceAccountPath(serviceAccountFile)?.let(::isReadableFile) == true
    } catch (_: InvalidPathException) {
        false
    }

    private fun serviceAccountPath(serviceAccountFile: String): Path? = serviceAccountFile
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let(Path::of)

    companion object {
        private val logger = LoggerFactory.getLogger(FcmClientConfig::class.java)
    }
}
