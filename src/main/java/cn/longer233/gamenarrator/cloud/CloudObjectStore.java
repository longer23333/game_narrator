package cn.longer233.gamenarrator.cloud;

import java.nio.file.Path;

public interface CloudObjectStore {
    void upload(String objectKey, Path source, String contentType, String sha256) throws Exception;
    void download(String objectKey, Path target, String expectedSha256) throws Exception;
}
