package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Cloud AI provider settings. The API key is stored only in
 * EncryptedSharedPreferences and is never written to SQLite, logs or archives.
 */
public final class AiCloudSettings {
    private static final String FILE = "ai_cloud";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_VISION_MODEL = "visionModel";
    private static final String KEY_TEXT_MODEL = "textModel";
    private static final String PREF_KEY = "apiKey";

    private final SharedPreferences prefs;

    public AiCloudSettings(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(context, FILE, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("无法初始化加密设置", error);
        }
    }

    public String provider() { return prefs.getString(KEY_PROVIDER, "DASHSCOPE"); }
    public String baseUrl() { return prefs.getString(KEY_BASE_URL, "https://dashscope.aliyuncs.com/compatible-mode/v1"); }
    public String visionModel() { return prefs.getString(KEY_VISION_MODEL, "qwen-vl-plus"); }
    public String textModel() { return prefs.getString(KEY_TEXT_MODEL, "qwen-plus"); }
    public boolean hasApiKey() { return !apiKey().isBlank(); }
    public String apiKey() { return prefs.getString(PREF_KEY, ""); }

    public void save(String provider, String baseUrl, String visionModel, String textModel, String keyValue) {
        prefs.edit()
                .putString(KEY_PROVIDER, provider == null ? "" : provider)
                .putString(KEY_BASE_URL, baseUrl == null ? "" : baseUrl)
                .putString(KEY_VISION_MODEL, visionModel == null ? "" : visionModel)
                .putString(KEY_TEXT_MODEL, textModel == null ? "" : textModel)
                .putString(PREF_KEY, keyValue == null ? "" : keyValue)
                .apply();
    }

    public void clearApiKey() {
        prefs.edit().remove(PREF_KEY).apply();
    }
}
