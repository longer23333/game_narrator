package cn.longer233.gamenarrator.importer;

public record MediaDownloadProgress(String percent, String downloadedBytes, String totalBytes,
                                    String speed, String eta) {
}
