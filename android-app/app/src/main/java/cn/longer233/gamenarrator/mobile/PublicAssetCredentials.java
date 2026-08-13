package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Provider keys are device-encrypted and never enter projects, SQLite, logs or exports. */
public final class PublicAssetCredentials {
    private static final String FILE = "public_asset_credentials";
    private static final String KEY_PEXELS = "pexels";
    private static final String KEY_PIXABAY = "pixabay";
    private final SharedPreferences prefs;

    public PublicAssetCredentials(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(context, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("无法初始化公共素材加密凭据", error);
        }
    }

    public String pexelsKey() { return prefs.getString(KEY_PEXELS, ""); }
    public String pixabayKey() { return prefs.getString(KEY_PIXABAY, ""); }
    public boolean hasPexels() { return !pexelsKey().isBlank(); }
    public boolean hasPixabay() { return !pixabayKey().isBlank(); }
    public void save(String pexels, String pixabay) {
        SharedPreferences.Editor edit = prefs.edit();
        if (pexels != null && !pexels.isBlank()) edit.putString(KEY_PEXELS, pexels.trim());
        if (pixabay != null && !pixabay.isBlank()) edit.putString(KEY_PIXABAY, pixabay.trim());
        edit.apply();
    }
    public void clear() { prefs.edit().clear().apply(); }
}
