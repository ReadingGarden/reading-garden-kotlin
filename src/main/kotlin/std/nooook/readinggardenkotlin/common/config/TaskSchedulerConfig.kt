package std.nooook.readinggardenkotlin.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock

@Configuration
class TaskSchedulerConfig {
    @Bean
    fun taskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("reading-garden-auth-")
            initialize()
        }

    @Bean
    fun utcClock(): Clock = Clock.systemUTC()
}
