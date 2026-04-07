package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
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
    fun fcmClient(firebaseProperties: FirebaseProperties): FcmClient {
        if (!hasUsableFirebaseConfig(firebaseProperties)) {
            logger.info(
                "Firebase FCM client is disabled. Falling back to no-op client. projectIdPresent={}, serviceAccountUsable={}",
                firebaseProperties.projectId.isNotBlank(),
                hasUsableServiceAccountPath(firebaseProperties),
            )
            return NoopFcmClient()
        }

        val firebaseAppName = "reading-garden-${UUID.randomUUID()}"

        return try {
            val firebaseApp = createFirebaseApp(firebaseAppName, firebaseProperties)
            FirebaseAdminFcmClient(
                firebaseApp = firebaseApp,
                firebaseMessagingSender = FirebaseAdminMessagingSender(FirebaseMessaging.getInstance(firebaseApp)),
            )
        } catch (exception: Exception) {
            logger.error(
                "Firebase FCM client initialization failed. Falling back to no-op client. projectId={}, serviceAccountFile={}",
                firebaseProperties.projectId.trim(),
                firebaseProperties.serviceAccountFile.trim(),
                exception,
            )
            NoopFcmClient()
        }
    }

    @Bean("pushClock")
    fun pushClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    private fun createFirebaseApp(
        appName: String,
        firebaseProperties: FirebaseProperties,
    ): FirebaseApp {
        val serviceAccountPath = requireNotNull(firebaseProperties.serviceAccountPath())
        Files.newInputStream(serviceAccountPath).use { inputStream ->
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(inputStream))
                .setProjectId(firebaseProperties.projectId.trim())
                .build()
            return FirebaseApp.initializeApp(options, appName)
        }
    }

    private fun hasUsableFirebaseConfig(firebaseProperties: FirebaseProperties): Boolean =
        firebaseProperties.projectId.isNotBlank() && hasUsableServiceAccountPath(firebaseProperties)

    private fun isReadableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isReadable(path)

    private fun hasUsableServiceAccountPath(firebaseProperties: FirebaseProperties): Boolean = try {
        firebaseProperties.serviceAccountPath()?.let(::isReadableFile) == true
    } catch (_: InvalidPathException) {
        false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FcmClientConfig::class.java)
    }
}
