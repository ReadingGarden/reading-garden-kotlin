package std.nooook.readinggardenkotlin.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.storage")
class StorageProperties {
    var imagesRoot: String = "/opt/reading-garden/data/images"
}
