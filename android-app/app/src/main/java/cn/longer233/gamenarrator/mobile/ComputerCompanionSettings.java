package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

final class ComputerCompanionSettings {
    private final SharedPreferences preferences;

    ComputerCompanionSettings(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            preferences = EncryptedSharedPreferences.create(context, "computer-companion", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception error) { throw new IllegalStateException("无法打开电脑协同加密设置", error); }
    }

    String address() { return preferences.getString("address", "http://10.0.2.2:8080"); }
    String token() { return preferences.getString("token", ""); }
    void save(String address, String token) {
        preferences.edit().putString("address", address == null ? "" : address.trim())
                .putString("token", token == null ? "" : token.trim()).apply();
    }
}
