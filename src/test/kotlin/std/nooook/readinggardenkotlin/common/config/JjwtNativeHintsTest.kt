package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class JjwtNativeHintsTest {

    private val hints = RuntimeHints().also {
        JjwtNativeHints().registerHints(it, javaClass.classLoader)
    }

    @Test
    fun `registers jjwt-impl classes for reflection`() {
        val sampleClasses = listOf(
            "io.jsonwebtoken.impl.security.KeysBridge",
            "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyOperations",
            "io.jsonwebtoken.impl.DefaultJwtBuilder",
            "io.jsonwebtoken.impl.DefaultJwtParser",
        )

        for (className in sampleClasses) {
            val predicate = RuntimeHintsPredicates.reflection()
                .onType(Class.forName(className))
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
            assertTrue(predicate.test(hints), "$className should be registered")
        }
    }

    @Test
    fun `registers jjwt-jackson classes for reflection`() {
        val classNames = listOf(
            "io.jsonwebtoken.jackson.io.JacksonSerializer",
            "io.jsonwebtoken.jackson.io.JacksonDeserializer",
        )

        for (className in classNames) {
            val predicate = RuntimeHintsPredicates.reflection()
                .onType(Class.forName(className))
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
            assertTrue(predicate.test(hints), "$className should be registered")
        }
    }

    @Test
    fun `registers ServiceLoader resources`() {
        val predicate = RuntimeHintsPredicates.resource()
            .forResource("META-INF/services/io.jsonwebtoken.io.Serializer")
        assertTrue(predicate.test(hints))
    }
}
