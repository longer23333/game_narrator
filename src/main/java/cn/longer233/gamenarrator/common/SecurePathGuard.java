package cn.longer233.gamenarrator.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class SecurePathGuard {
    private SecurePathGuard() { }

    public static Path prepareRoot(Path configuredRoot) throws IOException {
        Path root = configuredRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        rejectLinks(root);
        return root.toRealPath();
    }

    public static boolean isOwned(Path candidate, Path configuredRoot) {
        try {
            Path root = configuredRoot.toAbsolutePath().normalize();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false;
            rejectLinks(root);
            Path realRoot = root.toRealPath();
            Path path = candidate.toAbsolutePath().normalize();
            if (!path.startsWith(root) || path.equals(root)) return false;
            Path current = root;
            for (Path segment : root.relativize(path)) {
                current = current.resolve(segment);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) return false;
            }
            Path parent = path.getParent();
            return parent != null && parent.toRealPath().startsWith(realRoot);
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private static void rejectLinks(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path segment : path) {
            current = current == null ? segment : current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in managed storage: " + current);
            }
        }
    }
}
