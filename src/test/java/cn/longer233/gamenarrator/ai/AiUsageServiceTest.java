package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalUserContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void ignoresNonFinitePricesInsteadOfCorruptingUsageState() throws Exception {
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString());
        service.record("TEST","bad-pricing",100,50,25,Double.NaN,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
        AiUsageService.UsageSnapshot value=service.snapshot();
        assertEquals(0,value.turnCost());
        assertEquals(0,value.todayCost());
        assertFalse(java.nio.file.Files.readString(temporary.resolve("config/ai-usage.json")).contains("NaN"));
    }

    @Test
    void saturatesTokenCountersInsteadOfOverflowing() {
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString());
        service.record("TEST","large",Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,0,0,0);
        service.record("TEST","large",1,1,1,0,0,0);
        AiUsageService.UsageSnapshot value=service.snapshot();
        assertEquals(Long.MAX_VALUE,value.sessionInput());
        assertEquals(Long.MAX_VALUE,value.sessionOutput());
        assertEquals(Long.MAX_VALUE,value.sessionCached());
        assertEquals(100,value.cachePercent());
    }

    @Test
    void repairsNegativePersistedCountersBeforeAccumulating() throws Exception {
        Path config=temporary.resolve("config");
        java.nio.file.Files.createDirectories(config);
        java.nio.file.Files.writeString(config.resolve("ai-usage.json"),
                "{\"date\":\""+java.time.LocalDate.now()+"\",\"input\":-1,\"output\":-2,\"cached\":-3,\"cost\":-4}");
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString());
        service.record("TEST","repair",1,2,1,0,0,0);
        AiUsageService.UsageSnapshot value=service.snapshot();
        assertEquals(1,value.sessionInput());
        assertEquals(0,value.todayCost());
        com.fasterxml.jackson.databind.JsonNode repaired=new ObjectMapper().readTree(config.resolve("ai-usage.json").toFile());
        assertEquals(1,repaired.path("input").asLong());
        assertEquals(2,repaired.path("output").asLong());
        assertEquals(1,repaired.path("cached").asLong());
        assertEquals(0,repaired.path("cost").asDouble());
    }

    @Test
    void databaseUsageSaturatesAtSchemaLimitsAndBoundsIdentityColumns() {
        String url="jdbc:h2:mem:ai-usage-bounds-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource source=new DriverManagerDataSource(url,"sa","");
        Flyway.configure().dataSource(source).load().migrate();
        JdbcTemplate jdbc=new JdbcTemplate(source);
        CurrentUserContext currentUser=mock(CurrentUserContext.class);
        when(currentUser.userId()).thenReturn(LocalUserContext.LOCAL_USER_ID);
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString(),jdbc,currentUser);
        String provider="P".repeat(80),model="M".repeat(240);

        service.record(provider,model,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,
                Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE);
        service.record(provider,model,1,1,1,1,1,1);

        java.util.Map<String,Object> row=jdbc.queryForMap("SELECT * FROM user_ai_usage_daily WHERE user_id=?",
                LocalUserContext.LOCAL_USER_ID);
        assertEquals(40,((String)row.get("PROVIDER")).length());
        assertEquals(160,((String)row.get("MODEL_NAME")).length());
        assertEquals(Long.MAX_VALUE,((Number)row.get("INPUT_TOKENS")).longValue());
        assertEquals(Long.MAX_VALUE,((Number)row.get("OUTPUT_TOKENS")).longValue());
        assertEquals(Long.MAX_VALUE,((Number)row.get("CACHED_TOKENS")).longValue());
        assertEquals(2,((Number)row.get("REQUEST_COUNT")).longValue());
        assertTrue(((BigDecimal)row.get("ESTIMATED_COST")).compareTo(new BigDecimal("9999999999.99999999"))<=0);
    }

    @Test
    void databaseUsageKeepsLongModelIdentitiesDistinctWithoutBreakingSurrogatePairs() {
        String url="jdbc:h2:mem:ai-usage-identity-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource source=new DriverManagerDataSource(url,"sa","");
        Flyway.configure().dataSource(source).load().migrate();
        JdbcTemplate jdbc=new JdbcTemplate(source);
        CurrentUserContext currentUser=mock(CurrentUserContext.class);
        when(currentUser.userId()).thenReturn(LocalUserContext.LOCAL_USER_ID);
        AiUsageService service=new AiUsageService(new ObjectMapper(),temporary.toString(),jdbc,currentUser);
        String shared="模型😀".repeat(50);

        service.record("CUSTOM-PROVIDER-"+"P".repeat(40)+"-A",shared+"-A",1,0,0,0,0,0);
        service.record("CUSTOM-PROVIDER-"+"P".repeat(40)+"-B",shared+"-B",2,0,0,0,0,0);

        var rows=jdbc.queryForList("SELECT provider,model_name,input_tokens FROM user_ai_usage_daily WHERE user_id=? ORDER BY input_tokens",
                LocalUserContext.LOCAL_USER_ID);
        assertEquals(2,rows.size());
        assertNotEquals(rows.get(0).get("PROVIDER"),rows.get(1).get("PROVIDER"));
        assertNotEquals(rows.get(0).get("MODEL_NAME"),rows.get(1).get("MODEL_NAME"));
        for (var row:rows) {
            String provider=(String)row.get("PROVIDER"),model=(String)row.get("MODEL_NAME");
            assertTrue(provider.length()<=40);
            assertTrue(model.length()<=160);
            assertFalse(provider.endsWith("\uFFFD"));
            assertFalse(model.endsWith("\uFFFD"));
        }
    }
}
