package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class HibernateNativeHintsTest {
    @Test
    fun `registers java reflect Parameter public methods for kotlin reflection`() {
        val hints = RuntimeHints()

        HibernateNativeHints().registerHints(hints, javaClass.classLoader)

        val predicate = RuntimeHintsPredicates.reflection()
            .onType(java.lang.reflect.Parameter::class.java)
            .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)

        assertTrue(predicate.test(hints))
    }
}
