package cn.longer233.gamenarrator.cloud;

import cn.longer233.gamenarrator.identity.LocalUserContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudSyncServiceTest {
    @TempDir Path storage;

    @Test void uploadsAndRestoresAnOwnedFileWithHashVerification() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:cloud-sync;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalUserContext user = new LocalUserContext();
        user.begin(LocalUserContext.LOCAL_USER_ID, "USER", true);
        InMemoryStore store = new InMemoryStore();
        @SuppressWarnings("unchecked") ObjectProvider<CloudObjectStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        CloudSyncService service = new CloudSyncService(jdbc, provider, user, storage.toString(),
                new CloudSyncRetryPolicy(8, 30, 3600, .2));
        Path source = storage.resolve("sources/source.mp4");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "portable project media");
        UUID localId = UUID.randomUUID();
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));

        service.enqueue(LocalUserContext.LOCAL_USER_ID, "SOURCE_MEDIA", localId, source, "video/mp4", hash, Files.size(source));
        service.uploadPending();
        Files.delete(source);
        Path restored = service.restore(localId, "SOURCE_MEDIA", source);

        assertThat(Files.readString(restored)).isEqualTo("portable project media");
        assertThat(jdbc.queryForObject("SELECT sync_status FROM cloud_sync_item WHERE local_id=?", String.class, localId))
                .isEqualTo("SYNCED");
    }

    @Test void stopsAfterMaximumAttemptsAndAllowsTheOwnerToRetry() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:cloud-sync-retry-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalUserContext user = new LocalUserContext();
        user.begin(LocalUserContext.LOCAL_USER_ID, "USER", true);
        InMemoryStore store = new InMemoryStore();
        store.failUploads = true;
        @SuppressWarnings("unchecked") ObjectProvider<CloudObjectStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        CloudSyncService service = new CloudSyncService(jdbc, provider, user, storage.toString(),
                new CloudSyncRetryPolicy(3, 10, 60, 0));
        Path source = storage.resolve("retry/source.mp4");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "retry me");
        UUID localId = UUID.randomUUID();
        service.enqueue(LocalUserContext.LOCAL_USER_ID, "SOURCE_MEDIA", localId, source,
                "video/mp4", "hash", Files.size(source));

        for (int attempt = 1; attempt <= 3; attempt++) {
            service.uploadPending();
            if (attempt < 3) jdbc.update(
                    "UPDATE cloud_sync_item SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00' WHERE local_id=?",
                    localId);
        }

        Map<String,Object> failed = jdbc.queryForMap(
                "SELECT id,sync_status,attempt_count,next_attempt_at FROM cloud_sync_item WHERE local_id=?", localId);
        assertThat(failed.get("SYNC_STATUS")).isEqualTo("PERMANENT_FAILURE");
        assertThat(failed.get("ATTEMPT_COUNT")).isEqualTo(3);
        assertThat(failed.get("NEXT_ATTEMPT_AT")).isNull();

        service.retry((UUID) failed.get("ID"));
        assertThat(jdbc.queryForMap("SELECT sync_status,attempt_count FROM cloud_sync_item WHERE local_id=?", localId))
                .containsEntry("SYNC_STATUS", "PENDING").containsEntry("ATTEMPT_COUNT", 0);
    }

    @Test void retryPolicyBacksOffExponentiallyAndCapsTheDelay() {
        CloudSyncRetryPolicy policy = new CloudSyncRetryPolicy(5, 10, 35, 0);
        UUID id = UUID.randomUUID();
        assertThat(policy.delay(1, id).toSeconds()).isEqualTo(10);
        assertThat(policy.delay(2, id).toSeconds()).isEqualTo(20);
        assertThat(policy.delay(3, id).toSeconds()).isEqualTo(35);
        assertThat(policy.exhausted(4)).isFalse();
        assertThat(policy.exhausted(5)).isTrue();
    }

    private static final class InMemoryStore implements CloudObjectStore {
        private byte[] value;
        private boolean failUploads;
        @Override public void upload(String objectKey, Path source, String contentType, String sha256) throws Exception {
            if (failUploads) throw new java.io.IOException("simulated network failure");
            value = Files.readAllBytes(source);
        }
        @Override public void download(String objectKey, Path target, String expectedSha256) throws Exception {
            Files.write(target, value);
        }
    }
}
