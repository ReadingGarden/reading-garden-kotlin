package std.nooook.readinggardenkotlin.common.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.module.SimpleModule
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

@Configuration
class JacksonConfig {

    @Bean
    fun flexibleLocalDateTimeCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            val module = SimpleModule().addDeserializer(
                LocalDateTime::class.java,
                FlexibleLocalDateTimeDeserializer(),
            )
            builder.addModule(module)
        }
}

/**
 * Dart의 DateTime.toString() 형식("2026-04-14 15:30:45.123456")과
 * ISO-8601 형식("2026-04-14T15:30:45") 모두 파싱할 수 있는 역직렬화기.
 */
class FlexibleLocalDateTimeDeserializer : StdDeserializer<LocalDateTime>(LocalDateTime::class.java) {

    companion object {
        private val FLEXIBLE_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter()
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDateTime {
        val text = p.text.trim()
        return if (text.contains('T')) {
            LocalDateTime.parse(text)
        } else {
            LocalDateTime.parse(text, FLEXIBLE_FORMATTER)
        }
    }
}
