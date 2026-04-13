package std.nooook.readinggardenkotlin.common.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference

class JjwtNativeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val reflectionCategories = arrayOf(
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_DECLARED_METHODS,
        )

        // Jwts static initializer가 Classes.newInstance()로 로드하는 클래스들
        val reflectionTargets = listOf(
            "io.jsonwebtoken.impl.security.KeysBridge",
            "io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms",
            "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyAlgorithms",
            "io.jsonwebtoken.impl.io.StandardCompressionAlgorithms",
            "io.jsonwebtoken.impl.DefaultJwtBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultJwtParserBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultJwtHeaderBuilder\$Supplier",
            "io.jsonwebtoken.impl.DefaultClaimsBuilder\$Supplier",
        )

        for (className in reflectionTargets) {
            hints.reflection().registerType(
                TypeReference.of(className),
                *reflectionCategories,
            )
        }

        // jjwt-jackson ServiceLoader 리소스
        val serviceResources = listOf(
            "META-INF/services/io.jsonwebtoken.io.Serializer",
            "META-INF/services/io.jsonwebtoken.io.Deserializer",
            "META-INF/services/io.jsonwebtoken.CompressionCodec",
        )

        for (resource in serviceResources) {
            hints.resources().registerPattern(resource)
        }

        // ServiceLoader 구현 클래스 리플렉션
        val serviceImplTargets = listOf(
            "io.jsonwebtoken.jackson.io.JacksonSerializer",
            "io.jsonwebtoken.jackson.io.JacksonDeserializer",
            "io.jsonwebtoken.impl.compression.DeflateCompressionAlgorithm",
            "io.jsonwebtoken.impl.compression.GzipCompressionAlgorithm",
        )

        for (className in serviceImplTargets) {
            hints.reflection().registerType(
                TypeReference.of(className),
                *reflectionCategories,
            )
        }
    }
}
