package std.nooook.readinggardenkotlin

import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import std.nooook.readinggardenkotlin.common.config.HibernateNativeHints

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportRuntimeHints(HibernateNativeHints::class)
class ReadingGardenKotlinApplication

fun main(args: Array<String>) {
    runApplication<ReadingGardenKotlinApplication>(*args)
}
