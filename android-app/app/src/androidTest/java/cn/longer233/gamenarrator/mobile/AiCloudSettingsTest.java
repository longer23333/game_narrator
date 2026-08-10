package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AiCloudSettingsTest {
    @Test public void cloudSettingsRoundTripEncryptedAndClearsKey() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AiCloudSettings settings = new AiCloudSettings(context);
        settings.save("OPENAI", "https://api.openai.com/v1", "gpt-4o", "gpt-4o-mini", "sk-test-123");

        AiCloudSettings reloaded = new AiCloudSettings(context);
        assertEquals("OPENAI", reloaded.provider());
        assertEquals("https://api.openai.com/v1", reloaded.baseUrl());
        assertEquals("gpt-4o", reloaded.visionModel());
        assertEquals("gpt-4o-mini", reloaded.textModel());
        assertTrue(reloaded.hasApiKey());
        assertEquals("sk-test-123", reloaded.apiKey());

        reloaded.clearApiKey();
        assertFalse(reloaded.hasApiKey());
    }
}
