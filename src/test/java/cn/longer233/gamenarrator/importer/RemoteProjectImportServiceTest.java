package cn.longer233.gamenarrator.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteProjectImportServiceTest {
    @Test
    void parsesYtDlpPercentValues() {
        assertThat(RemoteProjectImportService.percent("42.7%")).isEqualTo(42);
        assertThat(RemoteProjectImportService.percent("100%")).isEqualTo(100);
        assertThat(RemoteProjectImportService.percent(" 7 % ")).isEqualTo(7);
        assertThat(RemoteProjectImportService.percent("0")).isZero();
    }

    @Test
    void unparseablePercentStaysUnknownInsteadOfFakingProgress() {
        // 过去这里会返回固定的 10，让界面看起来"在下载"，实际上什么都没解析出来。
        assertThat(RemoteProjectImportService.percent(null)).isNull();
        assertThat(RemoteProjectImportService.percent("")).isNull();
        assertThat(RemoteProjectImportService.percent("N/A")).isNull();
        assertThat(RemoteProjectImportService.percent("--")).isNull();
    }
}
