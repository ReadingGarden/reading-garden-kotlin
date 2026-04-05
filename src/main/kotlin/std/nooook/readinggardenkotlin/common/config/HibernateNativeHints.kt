package std.nooook.readinggardenkotlin.common.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver

class HibernateNativeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val loader = classLoader ?: return
        val resolver = PathMatchingResourcePatternResolver(loader)

        registerHibernateLoggerImplementations(hints, resolver)
        registerHibernateI18nResources(hints)
    }

    private fun registerHibernateLoggerImplementations(
        hints: RuntimeHints,
        resolver: ResourcePatternResolver,
    ) {
        val resources = resolver.getResources("classpath*:org/hibernate/**/*_" + "$" + "logger.class")

        resources
            .mapNotNull { resource ->
                val location = resource.url.toString()
                val path = when {
                    location.contains("!/") -> location.substringAfter("!/")
                    location.contains("/org/hibernate/") ->
                        "org/hibernate/${location.substringAfter("/org/hibernate/")}"
                    else -> null
                }?.removePrefix("/")

                if (path == null || !path.endsWith(".class")) {
                    return@mapNotNull null
                }

                path.removeSuffix(".class").replace('/', '.')
            }
            .distinct()
            .forEach { className ->
                hints.reflection().registerType(
                    TypeReference.of(className),
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                )
            }
    }

    private fun registerHibernateI18nResources(hints: RuntimeHints) {
        hints.resources().registerPattern("org/hibernate/**/*.i18n.properties")
    }
}
