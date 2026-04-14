package std.nooook.readinggardenkotlin.common.config

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class FirebaseNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("admin_sdk.properties")
        hints.resources().registerPattern("google-api-client.properties")
        hints.resources().registerPattern("com/google/api/client/googleapis/google-api-client.properties")
        hints.resources().registerPattern("com/google/api/client/googleapis/google.p12")
        hints.resources().registerPattern("com/google/api/client/http/google-http-client.properties")
    }
}
