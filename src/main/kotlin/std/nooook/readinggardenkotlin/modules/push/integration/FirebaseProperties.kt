package std.nooook.readinggardenkotlin.modules.push.integration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@ConfigurationProperties(prefix = "app.firebase")
class FirebaseProperties {
    var projectId: String = ""
    var serviceAccountFile: String = ""

    fun serviceAccountPath(): Path? = serviceAccountFile
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let(Path::of)
}
