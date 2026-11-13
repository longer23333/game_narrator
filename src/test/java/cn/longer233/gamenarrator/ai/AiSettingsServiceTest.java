package cn.longer233.gamenarrator.ai;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.identity.LocalSecretCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSettingsServiceTest {
    @TempDir Path temporary;

    private AiSettingsService service() {
        return new AiSettingsService(new ObjectMapper(), temporary.toString(), "",
                mock(JdbcTemplate.class), mock(CurrentUserContext.class), mock(LocalSecretCipher.class));
    }

    @Test
    void repairsLegacyLocalSettingsWhenReadingStoredFiles() throws Exception {
        var mapper = new ObjectMapper();
        Files.createDirectories(temporary.resolve("config"));
        mapper.writeValue(temporary.resolve("config/ai-settings.json").toFile(),
                new AiSettingsService.Settings("LOCAL", "DASHSCOPE", "", "",
                        "qwen-vl-plus", "qwen-plus", 0d, 0d, 0d));
        var value = service().current();
        assertEquals("qwen2.5vl:3b", value.visionModel());
        assertEquals("qwen2.5vl:3b", value.textModel());
    }

    @Test
    void repairsLoggedInAccountSettingsUsedByDirectorReviews() throws Exception {
        var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE user_cloud_ai_config(user_id UUID, mode VARCHAR, provider VARCHAR, base_url VARCHAR, vision_model VARCHAR, text_model VARCHAR, api_key_ciphertext VARCHAR, input_price_per_million DOUBLE, output_price_per_million DOUBLE, cached_input_price_per_million DOUBLE)");
        var owner = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO user_cloud_ai_config VALUES(?,'LOCAL','DASHSCOPE','','qwen-vl-plus','qwen-plus','',0,0,0)", owner);
        var user = mock(CurrentUserContext.class);
        when(user.authenticated()).thenReturn(true);
        when(user.userId()).thenReturn(owner);
        var service = new AiSettingsService(new ObjectMapper(), temporary.toString(), "", jdbc, user, mock(LocalSecretCipher.class));
        var local = mock(OllamaChatBackend.class);
        var router = new AdaptiveAiChatClient(service, local, mock(CloudAiChatBackend.class),
                "qwen2.5vl:3b", "qwen2.5vl:3b", "LOCAL_FIRST");
        assertEquals("qwen2.5vl:3b", router.activeModel(false));
        router.chatJson("director review", java.util.List.of(), false, java.time.Duration.ofSeconds(5));
        org.mockito.Mockito.verify(local).chatJson(org.mockito.ArgumentMatchers.eq("director review"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("qwen2.5vl:3b"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void switchingToLocalReplacesUnchangedCloudModelsWithConfiguredDefaults() {
        var service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "localVisionModel", "custom-vision:3b");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "localTextModel", "custom-text:3b");
        var cloud = service.current();
        var saved = service.save(new AiSettingsService.Settings("LOCAL", cloud.provider(), "", cloud.baseUrl(),
                cloud.visionModel(), cloud.textModel(), 0d, 0d, 0d));
        assertEquals("custom-vision:3b", saved.visionModel());
        assertEquals("custom-text:3b", saved.textModel());
    }

    @Test
    void switchingOneRoleFromCloudAlsoInitializesTheOtherLocalRole() {
        var service = service();
        var saved = service.switchLocalModel("VISION", "custom-vision:7b");
        assertEquals("custom-vision:7b", saved.visionModel());
        assertEquals("qwen2.5vl:3b", saved.textModel());
        saved = service.switchLocalModel("TEXT", "custom-text:3b");
        assertEquals("custom-vision:7b", saved.visionModel());
        assertEquals("custom-text:3b", saved.textModel());
    }

    @Test
    void switchingModesPreservesExplicitlyChosenLocalModels() {
        var service = service();
        var saved = service.save(new AiSettingsService.Settings("LOCAL", "DASHSCOPE", "", "",
                "custom-vision:7b", "custom-text:3b", 0d, 0d, 0d));
        assertEquals("custom-vision:7b", saved.visionModel());
        assertEquals("custom-text:3b", service.current().textModel());
    }

    @Test
    void switchingBackToCloudReplacesLeakedLocalModelNames() {
        var service = service();
        var local = service.save(new AiSettingsService.Settings("LOCAL", "DASHSCOPE", "", "",
                "qwen2.5vl:3b", "qwen2.5vl:3b", 0d, 0d, 0d));
        assertEquals("LOCAL", local.mode());

        var cloud = service.save(new AiSettingsService.Settings("CLOUD", "DASHSCOPE", "", local.baseUrl(),
                local.visionModel(), local.textModel(), 0d, 0d, 0d));

        assertEquals("qwen-vl-plus", cloud.visionModel());
        assertEquals("qwen-plus", cloud.textModel());
        assertEquals("qwen-plus", service.current().textModel());
    }

    @Test
    void deepSeekKeepsLocalVisionModelAndRepairsOnlyItsTextModel() {
        var service = service();
        var saved = service.save(new AiSettingsService.Settings("CLOUD", "DEEPSEEK", "secret",
                "", "qwen2.5vl:3b", "qwen2.5vl:3b", 0d, 0d, 0d));

        assertEquals("qwen2.5vl:3b", saved.visionModel(), "DeepSeek 没有云端视觉模型，本地视觉模型名应保留");
        assertEquals("deepseek-chat", saved.textModel());
    }

    @Test
    void customCloudModelNamesWithoutOllamaTagArePreserved() {
        var service = service();
        var saved = service.save(new AiSettingsService.Settings("CLOUD", "DASHSCOPE", "", "",
                "qwen-vl-max-latest", "qwen-plus", 0d, 0d, 0d));
        assertEquals("qwen-vl-max-latest", saved.visionModel());
        assertEquals("qwen-plus", saved.textModel());
    }

    @Test
    void normalizesNonFinitePricesBeforePersisting() throws Exception {
        CurrentUserContext currentUser=mock(CurrentUserContext.class);
        when(currentUser.authenticated()).thenReturn(false);
        AiSettingsService service=new AiSettingsService(new ObjectMapper(),temporary.toString(),"",
                mock(JdbcTemplate.class),currentUser,mock(LocalSecretCipher.class));

        AiSettingsService.Settings saved=service.save(new AiSettingsService.Settings(
                "CLOUD","DASHSCOPE","","","","",Double.NaN,
                Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY));

        assertEquals(0,saved.inputPricePerMillion());
        assertEquals(0,saved.outputPricePerMillion());
        assertEquals(0,saved.cachedInputPricePerMillion());
        String json=Files.readString(temporary.resolve("config/ai-settings.json"));
        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
    }

    @Test
    void corruptSettingsFileFallsBackToDefaultsWithoutDeletingTheOriginalFile() throws Exception {
        Files.createDirectories(temporary.resolve("config"));
        Files.writeString(temporary.resolve("config/ai-settings.json"), "{ 坏掉的配置 ");

        var value = service().current();

        assertEquals("CLOUD", value.mode());
        assertEquals("DASHSCOPE", value.provider());
        assertTrue(Files.isRegularFile(temporary.resolve("config/ai-settings.json")),
                "损坏的配置文件应原样保留，便于排查与恢复");
    }

    @Test
    void fixesBuiltInProviderEndpointsAndRejectsPrivateCompatibleEndpoints() {
        CurrentUserContext currentUser=mock(CurrentUserContext.class);
        when(currentUser.authenticated()).thenReturn(false);
        AiSettingsService service=new AiSettingsService(new ObjectMapper(),temporary.toString(),"",
                mock(JdbcTemplate.class),currentUser,mock(LocalSecretCipher.class));

        AiSettingsService.Settings builtIn=service.save(new AiSettingsService.Settings(
                "CLOUD","OPENAI","secret","https://attacker.invalid/v1","vision","text",0d,0d,0d));
        assertEquals("https://api.openai.com/v1",builtIn.baseUrl());

        assertThrows(IllegalArgumentException.class,()->service.save(new AiSettingsService.Settings(
                "CLOUD","OPENAI_COMPATIBLE","secret","http://localhost:8080/v1","vision","text",0d,0d,0d)));
        assertThrows(IllegalArgumentException.class,()->service.save(new AiSettingsService.Settings(
                "CLOUD","OPENAI_COMPATIBLE","secret","https://127.0.0.1/v1","vision","text",0d,0d,0d)));
    }
}
