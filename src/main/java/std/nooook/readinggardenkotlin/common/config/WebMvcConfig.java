package std.nooook.readinggardenkotlin.common.config;

import jakarta.servlet.MultipartConfigElement;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final String imagesRoot;
    private final DataSize maxFileSize;
    private final DataSize maxRequestSize;

    public WebMvcConfig(
            @Value("${app.storage.images-root:/opt/reading-garden/data/images}") String imagesRoot,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize,
            @Value("${spring.servlet.multipart.max-request-size:10MB}") DataSize maxRequestSize
    ) {
        this.imagesRoot = imagesRoot;
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setLocation(Path.of(this.imagesRoot, "multipart-temp").toString());
        factory.setFileSizeThreshold(DataSize.ofBytes(0));
        factory.setMaxFileSize(this.maxFileSize);
        factory.setMaxRequestSize(this.maxRequestSize);
        return factory.createMultipartConfig();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalizedImagesRoot = Path.of(this.imagesRoot).toAbsolutePath().normalize().toString();
        registry
                .addResourceHandler("/images/**")
                .addResourceLocations("file:" + normalizedImagesRoot + "/")
                .setCacheControl(CacheControl.noCache());
    }
}
