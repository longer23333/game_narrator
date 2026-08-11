package cn.longer233.gamenarrator.cloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "game-narrator.cloud-sync.enabled", havingValue = "true")
public class S3CloudObjectStore implements CloudObjectStore {
    private final S3Client s3;
    private final CloudSyncProperties properties;

    public S3CloudObjectStore(S3Client s3, CloudSyncProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override public void upload(String key, Path source, String contentType, String sha256) throws Exception {
        long size = Files.size(source);
        if (size < properties.multipartThresholdBytes()) {
            s3.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                    .contentType(contentType).metadata(java.util.Map.of("sha256", sha256)).build(),
                    RequestBody.fromFile(source));
            return;
        }
        CreateMultipartUploadResponse created = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(properties.bucket()).key(key).contentType(contentType)
                .metadata(java.util.Map.of("sha256", sha256)).build());
        var completed = new ArrayList<CompletedPart>();
        try {
            long offset = 0;
            int part = 1;
            while (offset < size) {
                long length = Math.min(properties.multipartPartBytes(), size - offset);
                UploadPartResponse response;
                try (InputStream partInput = Files.newInputStream(source)) {
                    partInput.skipNBytes(offset);
                    response = s3.uploadPart(UploadPartRequest.builder().bucket(properties.bucket())
                                    .key(key).uploadId(created.uploadId()).partNumber(part).contentLength(length).build(),
                            RequestBody.fromInputStream(partInput, length));
                }
                completed.add(CompletedPart.builder().partNumber(part).eTag(response.eTag()).build());
                offset += length;
                part++;
            }
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(properties.bucket()).key(key)
                    .uploadId(created.uploadId()).multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completed).build()).build());
        } catch (Exception failure) {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(properties.bucket()).key(key)
                    .uploadId(created.uploadId()).build());
            throw failure;
        }
    }

    @Override public void download(String key, Path target, String expectedSha256) throws Exception {
        Path parent = target.toAbsolutePath().normalize().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".cloud-", ".part");
        try {
            s3.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build(), temporary);
            String actual = sha256(temporary);
            if (expectedSha256 != null && !expectedSha256.isBlank() && !expectedSha256.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("Downloaded object hash mismatch");
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024]; int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
