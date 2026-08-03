package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AiUsageServiceTest {
    @TempDir Path temporary;

    @Test
    void recordsTurnSessionCacheAndDailyCostWithoutSavingContent() throws Exception {
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString());
        service.record("OPENAI","example-model",1_000_000,100_000,250_000,2,8,0.5);
        AiUsageService.UsageSnapshot value=service.snapshot();
        assertEquals(1_000_000,value.turnInput());
        assertEquals(100_000,value.turnOutput());
        assertEquals(250_000,value.sessionCached());
        assertEquals(25d,value.cachePercent());
        assertEquals(2.425d,value.turnCost(),0.000001);
        assertEquals(value.turnCost(),value.todayCost(),0.000001);
        String saved=java.nio.file.Files.readString(temporary.resolve("config/ai-usage.json"));
        assertFalse(saved.contains("prompt"));
        assertFalse(saved.contains("response"));
    }
}
