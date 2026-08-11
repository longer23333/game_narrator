package cn.longer233.gamenarrator.effect;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.common.SecurePathGuard;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ColorLutService {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private final Path storageRoot;
    private final VideoTaskRepository tasks;
    private final CurrentUserContext current;
    private final ObjectMapper mapper;

    public ColorLutService(@Value("${game-narrator.storage-root}") String storageRoot,
                           VideoTaskRepository tasks, CurrentUserContext current, ObjectMapper mapper) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.tasks = tasks;
        this.current = current;
        this.mapper = mapper;
    }

    public LutView upload(UUID taskId, MultipartFile file) {
        requireTask(taskId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 .cube LUT 文件");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".cube")) throw new IllegalArgumentException("LUT 仅支持 .cube 格式");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("LUT 文件不能超过 8 MB");
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            int dimension = validate(content);
            Path output = lutPath(taskId);
            Files.createDirectories(output.getParent());
            AtomicArtifactWriter.writeText(output, content, StandardCharsets.UTF_8);
            AtomicArtifactWriter.writeJson(mapper, metadataPath(taskId),
                    java.util.Map.of("originalName", name, "dimension", dimension, "sizeBytes", file.getSize()));
            return new LutView(true, name, dimension, file.getSize());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("保存 LUT 失败：" + exception.getMessage(), exception);
        }
    }

    public LutView status(UUID taskId) {
        requireTask(taskId);
        Path lut = lutPath(taskId);
        if (!Files.isRegularFile(lut)) return new LutView(false, null, 0, 0);
        try {
            var metadata = mapper.readTree(metadataPath(taskId).toFile());
            return new LutView(true, metadata.path("originalName").asText("color-lut.cube"),
                    metadata.path("dimension").asInt(), Files.size(lut));
        } catch (Exception exception) {
            return new LutView(true, "color-lut.cube", 0, safeSize(lut));
        }
    }

    public void delete(UUID taskId) {
        requireTask(taskId);
        try {
            Files.deleteIfExists(lutPath(taskId));
            Files.deleteIfExists(metadataPath(taskId));
        } catch (Exception exception) {
            throw new IllegalStateException("清除 LUT 失败：" + exception.getMessage(), exception);
        }
    }

    public Path activePath(UUID taskId) {
        Path path = lutPath(taskId);
        return Files.isRegularFile(path) ? path : null;
    }

    static int validate(String content) {
        int size = 0;
        int values = 0;
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("TITLE")
                    || line.startsWith("DOMAIN_MIN") || line.startsWith("DOMAIN_MAX")) continue;
            if (line.startsWith("LUT_3D_SIZE")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 2) throw new IllegalArgumentException("LUT_3D_SIZE 格式错误");
                size = Integer.parseInt(parts[1]);
                if (size < 2 || size > 64) throw new IllegalArgumentException("LUT 维度必须在 2 到 64 之间");
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("LUT 色值必须为三列数字");
            for (String part : parts) if (!Double.isFinite(Double.parseDouble(part)))
                throw new IllegalArgumentException("LUT 包含无效数字");
            values++;
        }
        if (size == 0) throw new IllegalArgumentException("LUT 缺少 LUT_3D_SIZE");
        if (values != size * size * size) throw new IllegalArgumentException(
                "LUT 色值数量不匹配：需要 " + (size * size * size) + " 行，实际 " + values + " 行");
        return size;
    }

    private void requireTask(UUID taskId) {
        tasks.findByIdAndOwnerId(taskId, current.userId()).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    }
    private Path lutPath(UUID taskId) { return ownedTaskDirectory(taskId).resolve("color-lut.cube"); }
    private Path metadataPath(UUID taskId) { return ownedTaskDirectory(taskId).resolve("color-lut.json"); }
    private Path ownedTaskDirectory(UUID taskId) {
        Path path = storageRoot.resolve("tasks").resolve(taskId.toString()).normalize();
        if (!SecurePathGuard.isOwned(path, storageRoot)) throw new IllegalArgumentException("LUT 存储路径无效");
        return path;
    }
    private long safeSize(Path path) { try { return Files.size(path); } catch (Exception ignored) { return 0; } }
    public record LutView(boolean configured, String originalName, int dimension, long sizeBytes) { }
}
