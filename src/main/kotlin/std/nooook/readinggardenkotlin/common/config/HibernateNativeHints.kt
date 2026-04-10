package std.nooook.readinggardenkotlin.common.config

import org.springframework.aot.hint.ExecutableMode
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver

class HibernateNativeHints : RuntimeHintsRegistrar {
    private val springDocSchemaResourcePatterns = listOf(
        "classpath*:std/nooook/readinggardenkotlin/common/api/*.class",
        "classpath*:std/nooook/readinggardenkotlin/modules/*/controller/*.class",
    )

    private val hibernateEventListenerTypes = listOf(
        "org.hibernate.event.spi.AutoFlushEventListener",
        "org.hibernate.event.spi.ClearEventListener",
        "org.hibernate.event.spi.DeleteEventListener",
        "org.hibernate.event.spi.DirtyCheckEventListener",
        "org.hibernate.event.spi.EvictEventListener",
        "org.hibernate.event.spi.FlushEntityEventListener",
        "org.hibernate.event.spi.FlushEventListener",
        "org.hibernate.event.spi.InitializeCollectionEventListener",
        "org.hibernate.event.spi.LoadEventListener",
        "org.hibernate.event.spi.LockEventListener",
        "org.hibernate.event.spi.MergeEventListener",
        "org.hibernate.event.spi.PersistEventListener",
        "org.hibernate.event.spi.PostActionEventListener",
        "org.hibernate.event.spi.PostCollectionRecreateEventListener",
        "org.hibernate.event.spi.PostCollectionRemoveEventListener",
        "org.hibernate.event.spi.PostCollectionUpdateEventListener",
        "org.hibernate.event.spi.PostCommitDeleteEventListener",
        "org.hibernate.event.spi.PostCommitInsertEventListener",
        "org.hibernate.event.spi.PostCommitUpdateEventListener",
        "org.hibernate.event.spi.PostDeleteEventListener",
        "org.hibernate.event.spi.PostInsertEventListener",
        "org.hibernate.event.spi.PostLoadEventListener",
        "org.hibernate.event.spi.PostUpdateEventListener",
        "org.hibernate.event.spi.PostUpsertEventListener",
        "org.hibernate.event.spi.PreCollectionRecreateEventListener",
        "org.hibernate.event.spi.PreCollectionRemoveEventListener",
        "org.hibernate.event.spi.PreCollectionUpdateEventListener",
        "org.hibernate.event.spi.PreDeleteEventListener",
        "org.hibernate.event.spi.PreFlushEventListener",
        "org.hibernate.event.spi.PreInsertEventListener",
        "org.hibernate.event.spi.PreLoadEventListener",
        "org.hibernate.event.spi.PreUpdateEventListener",
        "org.hibernate.event.spi.PreUpsertEventListener",
        "org.hibernate.event.spi.RefreshEventListener",
        "org.hibernate.event.spi.ReplicateEventListener",
    )

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val loader = classLoader ?: return
        val resolver = PathMatchingResourcePatternResolver(loader)

        registerKotlinReflectionParameterHints(hints)
        registerSpringDocSchemaHints(hints, resolver)
        registerHibernateLoggerImplementations(hints, resolver)
        registerHibernateEventListenerArrays(hints)
        registerHibernateI18nResources(hints)
    }

    private fun registerKotlinReflectionParameterHints(hints: RuntimeHints) {
        hints.reflection().registerType(java.lang.reflect.Executable::class.java) { typeHint ->
            typeHint.withMethod("getParameters", emptyList(), ExecutableMode.INVOKE)
        }

        // kotlin-reflect resolves java.lang.reflect.Parameter#getName reflectively in native mode.
        hints.reflection().registerType(
            java.lang.reflect.Parameter::class.java,
            MemberCategory.INVOKE_PUBLIC_METHODS,
        )
    }

    private fun registerSpringDocSchemaHints(
        hints: RuntimeHints,
        resolver: ResourcePatternResolver,
    ) {
        // springdoc inspects controller/common DTOs through kotlin-reflect when generating /v3/api-docs in native mode.
        springDocSchemaResourcePatterns
            .asSequence()
            .flatMap { resolver.getResources(it).asSequence() }
            .mapNotNull(::toApplicationClassName)
            .distinct()
            .forEach { className ->
                hints.reflection().registerType(
                    TypeReference.of(className),
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                )
            }
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

    private fun registerHibernateEventListenerArrays(hints: RuntimeHints) {
        hibernateEventListenerTypes.forEach { className ->
            hints.reflection().registerType(TypeReference.of("$className[]"))
        }
    }

    private fun registerHibernateI18nResources(hints: RuntimeHints) {
        hints.resources().registerPattern("org/hibernate/**/*.i18n.properties")
    }

    private fun toApplicationClassName(resource: Resource): String? {
        val location = resource.url.toString()
        val path = when {
            location.contains("!/") -> location.substringAfter("!/")
            location.contains("/std/nooook/readinggardenkotlin/") ->
                "std/nooook/readinggardenkotlin/${location.substringAfter("/std/nooook/readinggardenkotlin/")}"
            else -> null
        }?.removePrefix("/")

        if (path == null || !path.endsWith(".class")) {
            return null
        }

        val className = path.removeSuffix(".class").replace('/', '.')
        if (
            className.endsWith("Controller") ||
            className.endsWith("Kt") ||
            className.endsWith("package-info") ||
            className.endsWith("module-info") ||
            '$' in className
        ) {
            return null
        }

        return className
    }
}
