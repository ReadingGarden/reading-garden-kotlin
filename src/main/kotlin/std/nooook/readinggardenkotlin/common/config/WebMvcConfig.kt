package std.nooook.readinggardenkotlin.common.config

import jakarta.servlet.MultipartConfigElement
import java.nio.file.Path
import org.springframework.boot.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import std.nooook.readinggardenkotlin.common.storage.StorageProperties

@Configuration
class WebMvcConfig(
    private val storageProperties: StorageProperties,
    @Value("\${spring.servlet.multipart.max-file-size:10MB}")
    private val maxFileSize: DataSize,
    @Value("\${spring.servlet.multipart.max-request-size:10MB}")
    private val maxRequestSize: DataSize,
) : WebMvcConfigurer {
    @Bean
    fun multipartConfigElement(): MultipartConfigElement {
        val factory = MultipartConfigFactory()
        factory.setLocation(Path.of(storageProperties.imagesRoot, "multipart-temp").toString())
        factory.setFileSizeThreshold(DataSize.ofBytes(0))
        factory.setMaxFileSize(maxFileSize)
        factory.setMaxRequestSize(maxRequestSize)
        return factory.createMultipartConfig()
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val imagesRoot = Path.of(storageProperties.imagesRoot).toAbsolutePath().normalize().toString()
        registry
            .addResourceHandler("/images/**")
            .addResourceLocations("file:$imagesRoot/")
            .setCacheControl(CacheControl.noCache())
    }
}
