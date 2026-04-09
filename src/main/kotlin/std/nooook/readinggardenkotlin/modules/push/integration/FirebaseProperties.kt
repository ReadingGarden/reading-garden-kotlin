package std.nooook.readinggardenkotlin.modules.push.integration

import java.nio.file.Path
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.firebase")
data class FirebaseProperties(
    val projectId: String = "",
    val serviceAccountFile: String = "",
) {
    fun serviceAccountPath(): Path? = serviceAccountFile
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let(Path::of)
}
