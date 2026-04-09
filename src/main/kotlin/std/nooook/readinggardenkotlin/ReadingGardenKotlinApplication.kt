package std.nooook.readinggardenkotlin

import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import std.nooook.readinggardenkotlin.common.config.HibernateNativeHints
import std.nooook.readinggardenkotlin.common.config.JjwtNativeHints

@SpringBootApplication
@EnableScheduling
@ImportRuntimeHints(HibernateNativeHints::class, JjwtNativeHints::class)
class ReadingGardenKotlinApplication

fun main(args: Array<String>) {
    runApplication<ReadingGardenKotlinApplication>(*args)
}
