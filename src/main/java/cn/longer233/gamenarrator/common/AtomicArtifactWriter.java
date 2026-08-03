package cn.longer233.gamenarrator.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Publishes generated artifacts only after their complete contents are on disk. */
public final class AtomicArtifactWriter {
    private AtomicArtifactWriter() {
    }

    public static void writeJson(ObjectMapper objectMapper, Path target, Object value) throws IOException {
        write(target, temporary -> objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value));
    }

    public static void writeText(Path target, String value, Charset charset) throws IOException {
        write(target, temporary -> Files.writeString(temporary, value, charset,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
    }

    private static void write(Path target, TemporaryWriter writer) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("Artifact has no parent directory: " + target);
        Files.createDirectories(parent);
        Path temporary = parent.resolve("." + normalized.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    private interface TemporaryWriter {
        void write(Path temporary) throws IOException;
    }
}
