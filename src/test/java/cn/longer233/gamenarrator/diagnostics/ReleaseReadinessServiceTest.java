package cn.longer233.gamenarrator.diagnostics;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.longer233.gamenarrator.cloud.CloudSyncProperties;
import cn.longer233.gamenarrator.storage.StorageAdminService;
import cn.longer233.gamenarrator.storage.StorageAdminView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
 @Test void copiedOldOrWrongCommitEvidenceCannotBecomeGreen() throws Exception {
  Path root=Files.createTempDirectory("readiness-semantic");Files.createDirectories(root.resolve("artifacts"));Files.createDirectories(root.resolve(".git/refs/heads"));
  Files.writeString(root.resolve("pom.xml"),"<project><artifactId>game-narrator</artifactId><version>2.2.21</version></project>");
  String current="1111111111111111111111111111111111111111";Files.writeString(root.resolve(".git/HEAD"),"ref: refs/heads/main\n");Files.writeString(root.resolve(".git/refs/heads/main"),current+"\n");
  String gates="\"migrationBaseline\":\"passed\",\"documentation\":\"passed\",\"frontend\":\"passed\",\"android\":\"passed\",\"androidEmulator\":\"passed\",\"backend\":\"passed\",\"performanceBaseline\":\"passed\"";
  Files.writeString(root.resolve("artifacts/ci-release-result.json"),"{\"status\":\"passed\",\"commit\":\"2222222222222222222222222222222222222222\",\"runUrl\":\"https://github.com/a/b/actions/runs/1\",\"completedAt\":\""+Instant.now()+"\",\"gates\":{"+gates+"}}");
  Files.writeString(root.resolve("artifacts/release-performance-gate.json"),"{\"schemaVersion\":1,\"commit\":\""+current+"\",\"completedAt\":\""+Instant.now()+"\"}");
  Files.writeString(root.resolve("artifacts/android-upgrade-2.2.4-current.json"),"{\"fromVersion\":\"2.2.4\",\"toVersion\":\"2.2.21\",\"completedAt\":\""+Instant.now().minusSeconds(8*86400)+"\",\"apkUpgrade\":true,\"databaseIntegrity\":true,\"projectIntegrity\":true,\"mediaIntegrity\":true,\"device\":{\"serial\":\"device-1\"}}");
  var diagnostics=mock(SystemDiagnosticsService.class);when(diagnostics.inspect()).thenReturn(Map.of("ffmpegAvailable",true,"whisperAvailable",true,"visionModelAvailable",true));
  var storage=mock(StorageAdminService.class);when(storage.inspect()).thenReturn(new StorageAdminView("x","NTFS",100,80,10,0,0,0,0,java.util.List.of()));
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(45);
  var report=new ReleaseReadinessService(diagnostics,storage,jdbc,new CloudSyncProperties(false,"","","","","",false,1,1),root.toString(),new ObjectMapper()).inspect();
  assertTrue(report.checks().stream().anyMatch(c->c.id().equals("ci")&&c.status().equals("RED")));
  assertTrue(report.checks().stream().anyMatch(c->c.id().equals("performance")&&c.status().equals("RED")));
  assertTrue(report.checks().stream().anyMatch(c->c.id().equals("android")&&c.status().equals("YELLOW")));
 }
 @Test void futureTimestampAndWrongAndroidTargetAreRejected() throws Exception {
  Path root=Files.createTempDirectory("readiness-future");Files.createDirectories(root.resolve("artifacts"));Files.writeString(root.resolve("pom.xml"),"<project><artifactId>game-narrator</artifactId><version>2.2.21</version></project>");
  Files.writeString(root.resolve("artifacts/android-upgrade-2.2.4-current.json"),"{\"fromVersion\":\"2.2.4\",\"toVersion\":\"2.2.20\",\"completedAt\":\""+Instant.now().plusSeconds(3600)+"\",\"apkUpgrade\":true,\"databaseIntegrity\":true,\"projectIntegrity\":true,\"mediaIntegrity\":true,\"device\":{\"serial\":\"device-1\"}}");
  var diagnostics=mock(SystemDiagnosticsService.class);when(diagnostics.inspect()).thenReturn(Map.of("ffmpegAvailable",true,"whisperAvailable",true,"visionModelAvailable",true));
  var storage=mock(StorageAdminService.class);when(storage.inspect()).thenReturn(new StorageAdminView("x","NTFS",100,80,10,0,0,0,0,java.util.List.of()));
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(45);
  var report=new ReleaseReadinessService(diagnostics,storage,jdbc,new CloudSyncProperties(false,"","","","","",false,1,1),root.toString(),new ObjectMapper()).inspect();
  assertTrue(report.checks().stream().anyMatch(c->c.id().equals("android")&&c.status().equals("RED")));
 }
}
