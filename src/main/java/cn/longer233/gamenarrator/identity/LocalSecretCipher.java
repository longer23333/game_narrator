package cn.longer233.gamenarrator.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class LocalSecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public LocalSecretCipher(@Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String root,
                             @Value("${game-narrator.security.master-key:${GAME_NARRATOR_SECRET_KEY:}}") String override) {
        try {
            byte[] bytes;
            if (override != null && !override.isBlank()) bytes = Base64.getDecoder().decode(override.strip());
            else {
                Path file=Path.of(root).toAbsolutePath().normalize().resolve("config/local-master.key");
                Path marker=file.resolveSibling("local-master.key.initialized");
                if(Files.isRegularFile(file)) {
                    bytes=Base64.getDecoder().decode(Files.readString(file).strip());
                    if(!Files.exists(marker)) Files.writeString(marker,"v1");
                } else {
                    if(Files.exists(marker)) throw new IllegalStateException("本地主密钥已丢失，请从备份恢复 local-master.key，或重新配置所有加密凭据");
                    bytes=new byte[32];random.nextBytes(bytes);Files.createDirectories(file.getParent());
                    Files.writeString(file,Base64.getEncoder().encodeToString(bytes));
                    Files.writeString(marker,"v1");
                }
            }
            if(bytes.length!=32) throw new IllegalArgumentException("主密钥必须是 32 字节 Base64");
            key=new SecretKeySpec(bytes,"AES");
        } catch(Exception e){throw new IllegalStateException("无法初始化本地密钥",e);}
    }
    public String encrypt(String plain){if(plain==null||plain.isBlank())return null;try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] encrypted=c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] all=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);return "v1:"+Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("无法加密 API Key",e);}}
    public String decrypt(String value){if(value==null||value.isBlank())return "";try{byte[] all=Base64.getDecoder().decode(value.substring(3));byte[] iv=java.util.Arrays.copyOfRange(all,0,12),data=java.util.Arrays.copyOfRange(all,12,all.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(c.doFinal(data),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("无法解密 API Key",e);}}
}
