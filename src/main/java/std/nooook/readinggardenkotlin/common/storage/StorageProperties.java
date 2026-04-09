package std.nooook.readinggardenkotlin.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String imagesRoot = "/opt/reading-garden/data/images";

    public String getImagesRoot() {
        return imagesRoot;
    }

    public void setImagesRoot(String imagesRoot) {
        this.imagesRoot = imagesRoot;
    }
}
