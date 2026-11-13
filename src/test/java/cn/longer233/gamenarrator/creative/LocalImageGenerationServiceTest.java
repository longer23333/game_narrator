package cn.longer233.gamenarrator.creative;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageGenerationServiceTest {
    @TempDir Path temporary;

    @Test
    void rejectsPathSeparatorsBeforeCallingEngine() {
        var service = new LocalImageGenerationService(new ObjectMapper(), null, UUID::randomUUID,
                temporary.toString(), "", "test", 8);
        for (String kind : new String[]{"../../outside", "a/../../../outside", "a\\..\\outside"}) {
            assertThatThrownBy(() -> service.generate(request(kind, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAnotherUsersReferenceAndOwnedReferencesOutsideStorage() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE external_asset(id UUID PRIMARY KEY, local_path VARCHAR)");
        jdbc.execute("CREATE TABLE user_external_asset(user_id UUID, asset_id UUID)");
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        Path root = Files.createDirectories(temporary.resolve("storage"));
        Path reference = Files.writeString(root.resolve("reference.png"), "private image");
        jdbc.update("INSERT INTO external_asset VALUES(?,?)", asset, reference.toString());
        jdbc.update("INSERT INTO user_external_asset VALUES(?,?)", owner, asset);
        var service = new LocalImageGenerationService(new ObjectMapper(), jdbc, () -> stranger,
                root.toString(), "http://127.0.0.1:1", "test", 8);
        assertThatThrownBy(() -> service.generate(request("CHARACTER", asset)))
                .hasRootCauseInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);

        Path outside = Files.writeString(temporary.resolve("outside.png"), "private image");
        jdbc.update("UPDATE external_asset SET local_path=? WHERE id=?", outside.toString(), asset);
        var ownerService = new LocalImageGenerationService(new ObjectMapper(), jdbc, () -> owner,
                root.toString(), "http://127.0.0.1:1", "test", 8);
        assertThatThrownBy(() -> ownerService.generate(request("CHARACTER", asset)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("参考素材路径不安全");
        jdbc.execute("SHUTDOWN");
    }

    // 回归：引擎端口关闭时 ConnectException 的 message 可能为 null，旧实现用 Map.of(...,"message",e.getMessage())
    // 会抛 NPE，把「引擎不可达」变成 500。现在的具名 Map 必须给出可展示的降级信息。
    @Test
    void reportsEngineUnreachableWithoutCrashingWhenPortIsClosed() {
        var service = new LocalImageGenerationService(new ObjectMapper(), null, UUID::randomUUID,
                temporary.toString(), "http://127.0.0.1:1", "test-model", 8);
        var info = service.capabilities();
        assertThat(info)
                .containsEntry("configured", true)
                .containsEntry("reachable", false)
                .containsEntry("engine", "AUTOMATIC1111");
        assertThat((String) info.get("message")).isNotBlank();
    }

    @Test
    void labelsComfyUiEngineHonestlyWhenEndpointIsComfyUi() {
        var service = new LocalImageGenerationService(new ObjectMapper(), null, UUID::randomUUID,
                temporary.toString(), "http://127.0.0.1:8188", "test-model", 8);
        var info = service.capabilities();
        assertThat(info)
                .containsEntry("configured", true)
                .containsEntry("reachable", false)
                .containsEntry("engine", "ComfyUI");
        assertThat((String) info.get("message")).isNotBlank();
    }

    private GeneratedImageRequest request(String kind, UUID reference) {
        return new GeneratedImageRequest(kind, "test", null, reference, null,
                256, 256, 1, 1L, null, null);
    }
}
