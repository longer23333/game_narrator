package cn.longer233.gamenarrator.importer;

public record MediaDownloadResult(String status,
                                  @com.fasterxml.jackson.annotation.JsonIgnore String localPath,
                                  String fileName, long sizeBytes,
                                  java.util.UUID assetId, String downloadUrl) {
}
