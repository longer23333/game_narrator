package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurePathGuardTest {
    @TempDir Path temporary;

    @Test
    void acceptsOnlyDescendantsInsideTheManagedRoot() throws Exception {
        Path root = SecurePathGuard.prepareRoot(temporary.resolve("storage"));
        assertThat(SecurePathGuard.isOwned(root.resolve("video.mp4"), root)).isTrue();
        assertThat(SecurePathGuard.isOwned(root, root)).isFalse();
        assertThat(SecurePathGuard.isOwned(temporary.resolve("outside.mp4"), root)).isFalse();
    }

    @Test
    void rejectsSymbolicLinkSegmentsWhenSupported() throws Exception {
        Path root = SecurePathGuard.prepareRoot(temporary.resolve("storage"));
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        Path link = root.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable for this test account");
        }
        assertThat(SecurePathGuard.isOwned(link.resolve("escaped.mp4"), root)).isFalse();
    }
}
