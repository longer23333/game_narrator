package cn.longer233.gamenarrator.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
import cn.longer233.gamenarrator.common.SecurePathGuard;
import jakarta.annotation.PostConstruct;

@Component
public class VideoStorage {

    private static final Logger log = LoggerFactory.getLogger(VideoStorage.class);
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("mp4", "mov", "mkv", "webm");

    private final Path root;

    public VideoStorage(@Value("${game-narrator.storage-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepareDirectories() throws IOException {
        Path safeRoot = SecurePathGuard.prepareRoot(root);
        Path sources = safeRoot.resolve("sources").normalize();
        Path temporary = safeRoot.resolve("upload-temp").normalize();
        if (!SecurePathGuard.isOwned(sources, safeRoot) || !SecurePathGuard.isOwned(temporary, safeRoot)) {
            throw new IOException("Invalid managed upload directories");
        }
        Files.createDirectories(sources);
        Files.createDirectories(temporary);
    }

    public String save(MultipartFile video) throws IOException {
        String originalName = video.getOriginalFilename() == null
                ? "video.mp4" : video.getOriginalFilename();
        String extension = extensionOf(originalName);
        log.info("VIDEO_VALIDATE originalName={} size={} contentType={} extension={}",
                originalName, video.getSize(), video.getContentType(), extension);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("VIDEO_REJECTED reason=unsupported_extension extension={}", extension);
            throw new IllegalArgumentException("仅支持 mp4、mov、mkv、webm 视频");
        }
        if (video.isEmpty()) {
            log.warn("VIDEO_REJECTED reason=empty_file originalName={}", originalName);
            throw new IllegalArgumentException("上传的视频文件为空");
        }
        Path safeRoot = SecurePathGuard.prepareRoot(root);
        Path target = safeRoot.resolve("sources").resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!SecurePathGuard.isOwned(target, safeRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        video.transferTo(target);
        if (Files.isSymbolicLink(target) || !SecurePathGuard.isOwned(target, safeRoot)) {
            Files.deleteIfExists(target);
            throw new IOException("Uploaded video escaped managed storage");
        }
        log.info("VIDEO_STORED path={} size={}", target, Files.size(target));
        return target.toString();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
