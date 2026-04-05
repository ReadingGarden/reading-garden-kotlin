package std.nooook.readinggardenkotlin

import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import std.nooook.readinggardenkotlin.common.config.HibernateNativeHints

@SpringBootApplication
@ImportRuntimeHints(HibernateNativeHints::class)
class ReadingGardenKotlinApplication

fun main(args: Array<String>) {
    runApplication<ReadingGardenKotlinApplication>(*args)
}
