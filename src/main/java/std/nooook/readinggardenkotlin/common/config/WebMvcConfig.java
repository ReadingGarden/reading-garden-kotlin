package std.nooook.readinggardenkotlin.common.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final String imagesRoot;

    public WebMvcConfig(@Value("${app.storage.images-root:/opt/reading-garden/data/images}") String imagesRoot) {
        this.imagesRoot = imagesRoot;
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
