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
        CloudSyncService service = new CloudSyncService(jdbc, provider, user, storage.toString());
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

    private static final class InMemoryStore implements CloudObjectStore {
        private byte[] value;
        @Override public void upload(String objectKey, Path source, String contentType, String sha256) throws Exception {
            value = Files.readAllBytes(source);
        }
        @Override public void download(String objectKey, Path target, String expectedSha256) throws Exception {
            Files.write(target, value);
        }
    }
}
