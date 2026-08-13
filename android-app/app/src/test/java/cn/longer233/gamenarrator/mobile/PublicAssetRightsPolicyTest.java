package cn.longer233.gamenarrator.mobile;
import static org.junit.Assert.assertTrue;
import java.io.File;
import org.junit.Test;

public final class PublicAssetRightsPolicyTest {
    private static PublicAsset asset(String provider) { return new PublicAsset(provider, "title", "creator", "license", "https://license", "https://source", "", "https://file", "image"); }
    @Test
    public void acceptsCompleteConfirmedProvenance() { PublicAssetRightsPolicy.requireDownloadable(asset("PEXELS"), true, true, false); }
    @Test
    public void bilibiliRequiresUploaderRights() {
        boolean rejected = false;
        try { PublicAssetRightsPolicy.requireDownloadable(asset("BILIBILI"), true, true, false); }
        catch (IllegalStateException expected) { rejected = true; }
        assertTrue(rejected);
    }
    @Test
    public void removesCompletedFileWhenCatalogWriteFails() throws Exception {
        File file = File.createTempFile("public-asset", ".jpg");
        try { PublicAssetDownloadTransaction.commit(file, ignored -> { throw new IllegalStateException("db"); }); }
        catch (IllegalStateException expected) { assertTrue(!file.exists()); }
    }
}
