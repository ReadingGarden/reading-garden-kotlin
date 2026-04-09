package std.nooook.readinggardenkotlin.modules.push.integration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FcmClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(FcmClientConfig.class);

    @Bean
    @ConditionalOnMissingBean(FcmClient.class)
    public FcmClient fcmClient(
            @Value("${app.firebase.project-id:}") String projectId,
            @Value("${app.firebase.service-account-file:}") String serviceAccountFile
    ) {
        if (!hasUsableFirebaseConfig(projectId, serviceAccountFile)) {
            logger.info(
                    "Firebase FCM client is disabled. Falling back to no-op client. projectIdPresent={}, serviceAccountUsable={}",
                    !projectId.isBlank(),
                    hasUsableServiceAccountPath(serviceAccountFile)
            );
            return new NoopFcmClient();
        }

        String firebaseAppName = "reading-garden-" + UUID.randomUUID();
        try {
            FirebaseApp firebaseApp = createFirebaseApp(firebaseAppName, projectId, serviceAccountFile);
            return new FirebaseAdminFcmClient(
                    new FirebaseAdminMessagingSender(FirebaseMessaging.getInstance(firebaseApp)),
                    firebaseApp
            );
        }
        catch (Exception exception) {
            logger.error(
                    "Firebase FCM client initialization failed. Falling back to no-op client. projectId={}, serviceAccountFile={}",
                    projectId.trim(),
                    serviceAccountFile.trim(),
                    exception
            );
            return new NoopFcmClient();
        }
    }

    @Bean("pushClock")
    public Clock pushClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    private FirebaseApp createFirebaseApp(
            String appName,
            String projectId,
            String serviceAccountFile
    ) throws Exception {
        Path serviceAccountPath = serviceAccountPath(serviceAccountFile);
        if (serviceAccountPath == null) {
            throw new IllegalArgumentException("Firebase service account file path is required");
        }
        try (var inputStream = Files.newInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .setProjectId(projectId.trim())
                    .build();
            return FirebaseApp.initializeApp(options, appName);
        }
    }

    private boolean hasUsableFirebaseConfig(String projectId, String serviceAccountFile) {
        return !projectId.isBlank() && hasUsableServiceAccountPath(serviceAccountFile);
    }

    private boolean isReadableFile(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    private boolean hasUsableServiceAccountPath(String serviceAccountFile) {
        try {
            Path path = serviceAccountPath(serviceAccountFile);
            return path != null && isReadableFile(path);
        }
        catch (InvalidPathException ignored) {
            return false;
        }
    }

    private Path serviceAccountPath(String serviceAccountFile) {
        String trimmed = serviceAccountFile.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return Path.of(trimmed);
    }
}
