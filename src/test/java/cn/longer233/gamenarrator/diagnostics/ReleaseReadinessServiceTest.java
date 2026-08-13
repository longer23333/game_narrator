package cn.longer233.gamenarrator.diagnostics;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.longer233.gamenarrator.cloud.CloudSyncProperties;
import cn.longer233.gamenarrator.storage.StorageAdminService;
import cn.longer233.gamenarrator.storage.StorageAdminView;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

class ReleaseReadinessServiceTest {
 @Test void unknownEvidenceIsYellowAndMissingFfmpegIsRed() throws Exception {
  var diagnostics=mock(SystemDiagnosticsService.class);when(diagnostics.inspect()).thenReturn(Map.of("ffmpegAvailable",false,"whisperAvailable",true,"visionModelAvailable",true,"externalProcessesActive",Map.of()));
  var storage=mock(StorageAdminService.class);when(storage.inspect()).thenReturn(new StorageAdminView("x","NTFS",100,80,10,0,0,0,0,java.util.List.of()));
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(45);
  var service=new ReleaseReadinessService(diagnostics,storage,jdbc,new CloudSyncProperties(false,"","","","","",false,1,1),Files.createTempDirectory("readiness").toString(),new ObjectMapper());
  var report=service.inspect();assertEquals("RED",report.overall());assertTrue(report.checks().stream().anyMatch(c->c.id().equals("android")&&c.status().equals("YELLOW")));assertTrue(report.checks().stream().anyMatch(c->c.id().equals("ffmpeg")&&c.status().equals("RED")));
 }
 @Test void invalidFreshEvidenceIsRedInsteadOfGreen() throws Exception {
  var root=Files.createTempDirectory("readiness-invalid");Files.createDirectories(root.resolve("artifacts"));Files.writeString(root.resolve("artifacts/release-performance-gate.json"),"{}");
  var diagnostics=mock(SystemDiagnosticsService.class);when(diagnostics.inspect()).thenReturn(Map.of("ffmpegAvailable",true,"whisperAvailable",true,"visionModelAvailable",true));
  var storage=mock(StorageAdminService.class);when(storage.inspect()).thenReturn(new StorageAdminView("x","NTFS",100,80,10,0,0,0,0,java.util.List.of()));
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(45);
  var service=new ReleaseReadinessService(diagnostics,storage,jdbc,new CloudSyncProperties(false,"","","","","",false,1,1),root.toString(),new ObjectMapper());
  var report=service.inspect();assertTrue(report.checks().stream().anyMatch(c->c.id().equals("performance")&&c.status().equals("RED")));
 }
}
