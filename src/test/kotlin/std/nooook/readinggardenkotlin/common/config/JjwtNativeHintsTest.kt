package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class JjwtNativeHintsTest {
    @Test
    fun `registers KeysBridge public methods for native jjwt bootstrap`() {
        val hints = RuntimeHints()

        JjwtNativeHints().registerHints(hints, javaClass.classLoader)

        val keysBridge = Class.forName("io.jsonwebtoken.impl.security.KeysBridge")
        val predicate = RuntimeHintsPredicates.reflection()
            .onType(keysBridge)
            .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)

        assertTrue(predicate.test(hints))
    }
}
