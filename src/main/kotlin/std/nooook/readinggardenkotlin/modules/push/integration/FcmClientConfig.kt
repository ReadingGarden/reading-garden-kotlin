package std.nooook.readinggardenkotlin.modules.push.integration

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class FcmClientConfig {
    @Bean
    @ConditionalOnMissingBean(FcmClient::class)
    fun fcmClient(): FcmClient = NoopFcmClient()

    @Bean("pushClock")
    fun pushClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}

private class NoopFcmClient : FcmClient {
    override fun sendToMany(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<Map<String, Any>> = emptyList()
}
