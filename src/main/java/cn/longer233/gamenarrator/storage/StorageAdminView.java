package cn.longer233.gamenarrator.storage;

import java.util.List;
import java.util.UUID;

public record StorageAdminView(String storagePath, String filesystemType, long totalBytes, long usableBytes, long reservedBytes,
                               long managedSourceBytes, long artifactBytes, int sourceCount, int artifactCount,
                               List<LargeSource> largestSources) {
    public record LargeSource(UUID taskId, String taskName, String path, long sizeBytes, String storageMode) { }
}
