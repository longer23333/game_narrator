package cn.longer233.gamenarrator.cloud;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(CloudSyncProperties.class)
public class S3CloudConfiguration {
    @Bean
    @ConditionalOnProperty(name = "game-narrator.cloud-sync.enabled", havingValue = "true")
    S3Client cloudS3Client(CloudSyncProperties properties) {
        if (properties.accessKey() == null || properties.accessKey().isBlank()
                || properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("Object storage credentials are required when cloud sync is enabled");
        }
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyle()).build());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }
}
