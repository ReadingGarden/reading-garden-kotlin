package std.nooook.readinggardenkotlin.common.config

import java.nio.file.Path
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import std.nooook.readinggardenkotlin.common.storage.StorageProperties

@Configuration
class WebMvcConfig(
    private val storageProperties: StorageProperties,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val imagesRoot = Path.of(storageProperties.imagesRoot).toAbsolutePath().normalize().toString()
        registry
            .addResourceHandler("/images/**")
            .addResourceLocations("file:$imagesRoot/")
            .setCacheControl(CacheControl.noCache())
    }
}
