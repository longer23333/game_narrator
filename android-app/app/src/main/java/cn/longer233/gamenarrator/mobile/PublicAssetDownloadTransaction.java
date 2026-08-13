package cn.longer233.gamenarrator.mobile;

import java.io.File;
import java.io.IOException;

/** Keeps downloaded files and catalog rows atomic from the caller's perspective. */
public final class PublicAssetDownloadTransaction {
    public interface CatalogWriter { long add(File file) throws Exception; }
    private PublicAssetDownloadTransaction() { }

    public static long commit(File completedFile, CatalogWriter writer) throws Exception {
        try {
            long id = writer.add(completedFile);
            if (id < 0) throw new IOException("素材库登记失败");
            return id;
        } catch (Exception error) {
            if (completedFile != null && completedFile.exists() && !completedFile.delete()) {
                completedFile.deleteOnExit();
            }
            throw error;
        }
    }

    public static void rollbackPartial(File partialFile) {
        if (partialFile != null && partialFile.exists() && !partialFile.delete()) partialFile.deleteOnExit();
    }
}
