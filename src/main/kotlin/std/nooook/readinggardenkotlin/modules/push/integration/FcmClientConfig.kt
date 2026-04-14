package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.auth.oauth2.GoogleCredentials
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId

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

        return try {
            val serviceAccountPath = requireNotNull(firebaseProperties.serviceAccountPath())
            val credentials = Files.newInputStream(serviceAccountPath).use { inputStream ->
                GoogleCredentials.fromStream(inputStream)
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            }
            val projectId = firebaseProperties.projectId.trim()
            logger.info("Firebase FCM client initialized with HTTP transport. projectId={}", projectId)
            HttpFcmClient(credentials = credentials, projectId = projectId)
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
