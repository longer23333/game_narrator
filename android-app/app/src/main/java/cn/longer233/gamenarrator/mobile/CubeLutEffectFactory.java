package cn.longer233.gamenarrator.mobile;

import androidx.media3.common.Effect;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.SingleColorLut;
import java.io.File;
import java.io.FileReader;

@UnstableApi
public final class CubeLutEffectFactory {
    private CubeLutEffectFactory() { }

    public static Effect fromPath(String path) {
        if (path == null || path.isBlank()) return null;
        File file = new File(path);
        if (!file.isFile()) throw new IllegalArgumentException("LUT 文件不存在或已被移除");
        try (FileReader reader = new FileReader(file)) {
            return SingleColorLut.createFromCube(CubeLutParser.parse(reader).cube());
        } catch (Exception error) {
            throw new IllegalArgumentException("无法加载 LUT：" + error.getMessage(), error);
        }
    }
}
