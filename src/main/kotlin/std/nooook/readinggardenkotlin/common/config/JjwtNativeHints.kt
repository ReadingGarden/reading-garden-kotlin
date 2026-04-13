package std.nooook.readinggardenkotlin.common.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

class JjwtNativeHints : RuntimeHintsRegistrar {

    private val jjwtClassResourcePatterns = listOf(
        "classpath*:io/jsonwebtoken/impl/**/*.class",
        "classpath*:io/jsonwebtoken/jackson/**/*.class",
    )

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val loader = classLoader ?: return
        val resolver = PathMatchingResourcePatternResolver(loader)

        registerJjwtReflectionHints(hints, resolver)
        registerServiceLoaderResources(hints)
    }

    private fun registerJjwtReflectionHints(
        hints: RuntimeHints,
        resolver: PathMatchingResourcePatternResolver,
    ) {
        val categories = arrayOf(
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_DECLARED_METHODS,
        )

        jjwtClassResourcePatterns
            .asSequence()
            .flatMap { resolver.getResources(it).asSequence() }
            .mapNotNull { resource ->
                val location = resource.url.toString()
                val path = when {
                    location.contains("!/") -> location.substringAfter("!/")
                    else -> null
                }?.removePrefix("/")

                if (path == null || !path.endsWith(".class")) return@mapNotNull null
                path.removeSuffix(".class").replace('/', '.')
            }
            .distinct()
            .forEach { className ->
                hints.reflection().registerType(TypeReference.of(className), *categories)
            }
    }

    private fun registerServiceLoaderResources(hints: RuntimeHints) {
        hints.resources().registerPattern("META-INF/services/io.jsonwebtoken.*")
    }
}
