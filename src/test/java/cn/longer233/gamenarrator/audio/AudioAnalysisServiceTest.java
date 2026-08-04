package cn.longer233.gamenarrator.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioAnalysisServiceTest {
    @Test
    void convertsRmsDbToBoundedEnergyScore() {
        assertThat(AudioAnalysisService.energyScore(-100)).isZero();
        assertThat(AudioAnalysisService.energyScore(-30)).isEqualTo(50);
        assertThat(AudioAnalysisService.energyScore(0)).isEqualTo(100);
    }
}
