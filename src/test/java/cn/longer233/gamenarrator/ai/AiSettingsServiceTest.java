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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSettingsServiceTest {
    @TempDir Path temporary;

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
