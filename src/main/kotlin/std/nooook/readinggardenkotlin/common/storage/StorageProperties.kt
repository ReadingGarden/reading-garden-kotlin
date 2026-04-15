package std.nooook.readinggardenkotlin.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.storage")
data class StorageProperties(
    val imagesRoot: String = "/opt/reading-garden/data/images",
)
