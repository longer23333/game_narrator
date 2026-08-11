package cn.longer233.gamenarrator.cloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("game-narrator.cloud-sync")
public record CloudSyncProperties(boolean enabled, String endpoint, String region, String bucket,
                                  String accessKey, String secretKey, boolean pathStyle,
                                  long multipartThresholdBytes, long multipartPartBytes) {
    public CloudSyncProperties {
        region = region == null || region.isBlank() ? "us-east-1" : region;
        bucket = bucket == null || bucket.isBlank() ? "game-narrator" : bucket;
        multipartThresholdBytes = multipartThresholdBytes <= 0 ? 64L * 1024 * 1024 : multipartThresholdBytes;
        multipartPartBytes = Math.max(5L * 1024 * 1024,
                multipartPartBytes <= 0 ? 16L * 1024 * 1024 : multipartPartBytes);
    }
}
