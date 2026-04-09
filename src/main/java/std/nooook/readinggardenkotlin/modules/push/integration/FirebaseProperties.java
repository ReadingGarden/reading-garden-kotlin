package std.nooook.readinggardenkotlin.modules.push.integration;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {
    private String projectId = "";
    private String serviceAccountFile = "";

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getServiceAccountFile() {
        return serviceAccountFile;
    }

    public void setServiceAccountFile(String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
    }

    public Path serviceAccountPath() {
        String trimmed = serviceAccountFile == null ? "" : serviceAccountFile.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return Path.of(trimmed);
    }
}
