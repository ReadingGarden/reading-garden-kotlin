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
    fun `registers KeysBridge for native jjwt bootstrap`() {
        val keysBridge = Class.forName("io.jsonwebtoken.impl.security.KeysBridge")
        val predicate = RuntimeHintsPredicates.reflection()
            .onType(keysBridge)
            .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)

        assertTrue(predicate.test(hints))
    }

    @Test
    fun `registers Jwts static initializer dependencies`() {
        val classNames = listOf(
            "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms",
            "io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyAlgorithms",
            "io.jsonwebtoken.impl.io.StandardCompressionAlgorithms",
        )

        for (className in classNames) {
            val predicate = RuntimeHintsPredicates.reflection()
                .onType(Class.forName(className))
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
            assertTrue(predicate.test(hints), "$className should be registered")
        }
    }

    @Test
    fun `registers jjwt builder and parser supplier classes`() {
        val classNames = listOf(
            "io.jsonwebtoken.impl.DefaultJwtBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultJwtParserBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultJwtHeaderBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultClaimsBuilder\$Supplier",
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
        val resources = listOf(
            "META-INF/services/io.jsonwebtoken.io.Serializer",
            "META-INF/services/io.jsonwebtoken.io.Deserializer",
            "META-INF/services/io.jsonwebtoken.CompressionCodec",
        )

        for (resource in resources) {
            val predicate = RuntimeHintsPredicates.resource().forResource(resource)
            assertTrue(predicate.test(hints), "$resource should be registered")
        }
    }

    @Test
    fun `registers Jackson serializer and deserializer implementations`() {
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
}
