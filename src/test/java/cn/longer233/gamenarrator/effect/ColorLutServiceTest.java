package cn.longer233.gamenarrator.effect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorLutServiceTest {
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
}
