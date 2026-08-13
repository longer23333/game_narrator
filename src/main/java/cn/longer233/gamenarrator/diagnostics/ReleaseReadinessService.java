package cn.longer233.gamenarrator.diagnostics;

import cn.longer233.gamenarrator.cloud.CloudSyncProperties;
import cn.longer233.gamenarrator.storage.StorageAdminService;
import cn.longer233.gamenarrator.storage.StorageAdminView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ReleaseReadinessService {
    public record Check(String id,String label,String status,String summary,String evidenceAt) { }
    public record Report(String overall,String generatedAt,List<Check> checks) { }
    private final SystemDiagnosticsService diagnostics;
    private final StorageAdminService storage;
    private final JdbcTemplate jdbc;
    private final CloudSyncProperties cloud;
    private final Path projectRoot;
    private final ObjectMapper mapper;

    public ReleaseReadinessService(SystemDiagnosticsService diagnostics, StorageAdminService storage,
                                   JdbcTemplate jdbc, CloudSyncProperties cloud,
                                   @Value("${game-narrator.project-root:.}") String projectRoot,
                                   ObjectMapper mapper) {
        this.diagnostics=diagnostics;this.storage=storage;this.jdbc=jdbc;this.cloud=cloud;
        this.projectRoot=Path.of(projectRoot).toAbsolutePath().normalize();
        this.mapper=mapper;
    }

    public Report inspect() {
        Map<String,Object> runtime=diagnostics.inspect();
        StorageAdminView capacity=storage.inspect();
        List<Check> checks=new ArrayList<>();
        checks.add(booleanCheck("ffmpeg","FFmpeg",runtime.get("ffmpegAvailable"),"视频渲染器"));
        boolean models=Boolean.TRUE.equals(runtime.get("whisperAvailable"))
                && Boolean.TRUE.equals(runtime.get("visionModelAvailable"));
        checks.add(new Check("models","AI 模型",models?"GREEN":"YELLOW",models?"转写与视觉模型可用":"部分本地模型不可用；对应能力会降级",now()));
        long reserve=Math.max(capacity.reservedBytes(),10L*1024*1024*1024);
        String disk=capacity.usableBytes()>reserve*2?"GREEN":capacity.usableBytes()>reserve?"YELLOW":"RED";
        checks.add(new Check("disk","磁盘",disk,"可用 "+gb(capacity.usableBytes())+" GB，安全保留 "+gb(reserve)+" GB",now()));
        String gpu=gpuStatus(runtime);
        checks.add(new Check("gpu","GPU",gpu,"GPU/硬件编码能力以诊断探测为准",now()));
        checks.add(databaseCheck());
        checks.add(cloudCheck());
        checks.add(evidenceCheck("android","Android 验收","artifacts/android-upgrade-2.2.4-current.json",this::validAndroidEvidence));
        checks.add(evidenceCheck("ci","CI 最近结果","artifacts/ci-release-result.json",this::validCiEvidence));
        checks.add(evidenceCheck("performance","专项性能","artifacts/release-performance-gate.json",this::validPerformanceEvidence));
        String overall=checks.stream().anyMatch(c->"RED".equals(c.status()))?"RED":
                checks.stream().anyMatch(c->"YELLOW".equals(c.status()))?"YELLOW":"GREEN";
        return new Report(overall,now(),List.copyOf(checks));
    }

    private Check databaseCheck(){
        try{Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Integer.class);
            return new Check("database","数据库迁移","GREEN",count+" 个迁移已成功应用",now());}
        catch(Exception error){return new Check("database","数据库迁移","RED","无法读取迁移历史："+safe(error.getMessage()),now());}
    }
    private Check cloudCheck(){
        if(!cloud.enabled())return new Check("cloud","云同步","YELLOW","云同步未启用（本地模式可用）",now());
        try{Integer failures=jdbc.queryForObject("SELECT COUNT(*) FROM cloud_sync_item WHERE sync_status IN ('FAILED','PERMANENT_FAILURE')",Integer.class);
            return new Check("cloud","云同步",failures!=null&&failures>0?"RED":"GREEN",(failures==null?0:failures)+" 个失败同步项",now());}
        catch(Exception error){return new Check("cloud","云同步","RED","无法读取同步队列："+safe(error.getMessage()),now());}
    }
    private Check evidenceCheck(String id,String label,String relative,java.util.function.Predicate<JsonNode> validator){
        Path path=projectRoot.resolve(relative).normalize();
        if(!path.startsWith(projectRoot)||!Files.isRegularFile(path))return new Check(id,label,"YELLOW","当前提交没有本机证据文件","");
        try{JsonNode evidence=mapper.readTree(path.toFile());String evidenceAt=evidence.path("completedAt").asText("");if(!validator.test(evidence))return new Check(id,label,"RED","证据文件内容不完整或未通过",evidenceAt);
            Instant completed=parseInstant(evidenceAt);boolean fresh=completed.isAfter(Instant.now().minusSeconds(7*86400));
            return new Check(id,label,fresh?"GREEN":"YELLOW",fresh?"证据在 7 天内完成":"证据完成时间已超过 7 天",completed.toString());}
        catch(Exception error){return new Check(id,label,"YELLOW","证据时间不可读","");}
    }
    private boolean validAndroidEvidence(JsonNode e){String target=e.path("toVersion").asText("");return "2.2.4".equals(e.path("fromVersion").asText())&&target.equals(currentVersion())&&validCompletedAt(e)&&e.path("apkUpgrade").asBoolean()&&e.path("databaseIntegrity").asBoolean()&&e.path("projectIntegrity").asBoolean()&&e.path("mediaIntegrity").asBoolean()&&!e.path("device").path("serial").asText("").isBlank();}
    private boolean validCiEvidence(JsonNode e){if(!"passed".equalsIgnoreCase(e.path("status").asText())||!sameCurrentCommit(e.path("commit").asText(""))||!e.path("runUrl").asText("").matches("https://github\\.com/.+/actions/runs/\\d+")||!validCompletedAt(e))return false;for(String gate:List.of("migrationBaseline","documentation","frontend","android","androidEmulator","backend","performanceBaseline"))if(!"passed".equals(e.path("gates").path(gate).asText()))return false;return true;}
    private boolean validPerformanceEvidence(JsonNode e){if(e.path("schemaVersion").asInt()!=2||e.path("minimumLongRunMinutes").asInt()<60||!sameCurrentCommit(e.path("commit").asText(""))||!validCompletedAt(e))return false;Map<String,String> validations=Map.of("input40gb","exact-sparse-length","diskLow","synthetic-low-space-rejection","gpuOom","explicit-oom-and-recovery-marker","ffmpegInterrupted","forced-nonzero-process-exit","longRun","minimum-duration-and-completion-marker","recovery","explicit-resume-integrity-marker");for(var entry:validations.entrySet()){JsonNode result=e.path("scenarios").path(entry.getKey());if(!"passed".equals(result.path("status").asText())||!entry.getValue().equals(result.path("validation").asText())||result.path("reportSha256").asText("").isBlank())return false;}return e.path("scenarios").path("longRun").path("elapsedSeconds").asDouble()>=3600&&!e.path("machine").path("gpu").asText("").isBlank()&&!e.path("machine").path("os").asText("").isBlank();}
    private boolean validCompletedAt(JsonNode evidence){try{Instant completed=parseInstant(evidence.path("completedAt").asText(""));return !completed.isAfter(Instant.now().plusSeconds(300));}catch(RuntimeException ignored){return false;}}
    private Instant parseInstant(String value){if(value==null||value.isBlank())throw new DateTimeParseException("blank",String.valueOf(value),0);return Instant.parse(value);}
    private boolean sameCurrentCommit(String evidenceCommit){String current=currentCommit();return current!=null&&evidenceCommit.matches("[0-9a-f]{40}")&&current.equalsIgnoreCase(evidenceCommit);}
    private String currentVersion(){try{String pom=Files.readString(projectRoot.resolve("pom.xml"));var match=java.util.regex.Pattern.compile("<artifactId>game-narrator</artifactId>\\s*<version>(\\d+\\.\\d+\\.\\d+)</version>").matcher(pom);return match.find()?match.group(1):null;}catch(Exception ignored){return null;}}
    private String currentCommit(){try{Path git=projectRoot.resolve(".git");String head=Files.readString(git.resolve("HEAD")).strip();if(head.matches("[0-9a-fA-F]{40}"))return head.toLowerCase(java.util.Locale.ROOT);if(head.startsWith("ref: ")){String ref=head.substring(5).strip();Path refPath=git.resolve(ref).normalize();if(refPath.startsWith(git)&&Files.isRegularFile(refPath))return Files.readString(refPath).strip().toLowerCase(java.util.Locale.ROOT);Path packed=git.resolve("packed-refs");if(Files.isRegularFile(packed))for(String line:Files.readAllLines(packed))if(line.endsWith(" "+ref))return line.substring(0,40).toLowerCase(java.util.Locale.ROOT);}}catch(Exception ignored){}return null;}
    private Check booleanCheck(String id,String label,Object value,String component){return new Check(id,label,Boolean.TRUE.equals(value)?"GREEN":"RED",component+(Boolean.TRUE.equals(value)?"可用":"不可用"),now());}
    private String gpuStatus(Map<String,Object> runtime){
        Object available=runtime.get("gpuAvailable");
        return Boolean.TRUE.equals(available)?"GREEN":Boolean.FALSE.equals(available)?"RED":"YELLOW";
    }
    private String gb(long bytes){return String.format(java.util.Locale.ROOT,"%.1f",bytes/1024d/1024d/1024d);}
    private String now(){return Instant.now().toString();}
    private String safe(String value){if(value==null)return "未知错误";return value.substring(0,Math.min(160,value.length()));}
}
