package cn.longer233.gamenarrator.effect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorLutServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsCompleteThreeDimensionalCube() {
        assertThat(ColorLutService.validate("""
                TITLE "Test"
                LUT_3D_SIZE 2
                0 0 0
                0 0 1
                0 1 0
                0 1 1
                1 0 0
                1 0 1
                1 1 0
                1 1 1
                """)).isEqualTo(2);
    }

    @Test
    void rejectsTruncatedOrOversizedCube() {
        assertThatThrownBy(() -> ColorLutService.validate("LUT_3D_SIZE 2\n0 0 0\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("需要 8 行");
        assertThatThrownBy(() -> ColorLutService.validate("LUT_3D_SIZE 65\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 到 64");
    }

    @Test
    void readsDimensionFromCubeFileWhenMetadataIsLost() throws Exception {
        Path lut = Files.writeString(temporaryDirectory.resolve("color-lut.cube"),
                "TITLE \"x\"\n# 注释\nLUT_3D_SIZE 33\n0 0 0\n");

        assertThat(ColorLutService.readDimension(lut)).isEqualTo(33);
    }

    @Test
    void missingDimensionReturnsZeroInsteadOfFabricatingAValue() throws Exception {
        Path withoutSize = Files.writeString(temporaryDirectory.resolve("no-size.cube"), "0 0 0\n");

        assertThat(ColorLutService.readDimension(withoutSize)).isZero();
        assertThat(ColorLutService.readDimension(temporaryDirectory.resolve("missing.cube"))).isZero();
    }
}
