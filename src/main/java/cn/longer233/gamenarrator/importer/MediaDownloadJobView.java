package cn.longer233.gamenarrator.importer;

public record MediaDownloadJobView(String id, String status, MediaDownloadProgress progress,
                                   MediaDownloadResult result, String error) {
}
