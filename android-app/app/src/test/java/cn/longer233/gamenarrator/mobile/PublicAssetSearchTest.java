package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class PublicAssetSearchTest {
    @Test public void parsesWikimediaCommonsResults() throws Exception {
        String json = "{\"query\":{\"pages\":{\"12\":{\"title\":\"File:Example.png\",\"descriptionurl\":\"https://commons.wikimedia.org/wiki/File:Example.png\","
                + "\"imageinfo\":[{\"url\":\"https://upload.wikimedia.org/x.png\",\"thumburl\":\"https://upload.wikimedia.org/x-thumb.png\","
                + "\"mime\":\"image/png\",\"extmetadata\":{\"Artist\":{\"value\":\"Alice\"},\"LicenseShortName\":{\"value\":\"CC BY-SA 4.0\"},"
                + "\"LicenseUrl\":{\"value\":\"https://creativecommons.org/licenses/by-sa/4.0/\"}}}]}}}}";
        List<PublicAsset> assets = PublicAssetSearch.parseWikimedia(json);
        assertEquals(1, assets.size());
        PublicAsset asset = assets.get(0);
        assertEquals("WIKIMEDIA", asset.provider());
        assertEquals("File:Example.png", asset.title());
        assertEquals("Alice", asset.creator());
        assertEquals("CC BY-SA 4.0", asset.license());
        assertEquals("image", asset.mediaType());
    }

    @Test public void parsesOpenverseImageResults() throws Exception {
        String json = "{\"results\":[{\"title\":\"Sunset\",\"creator\":\"Bob\",\"license\":\"by\","
                + "\"license_url\":\"https://creativecommons.org/licenses/by/4.0/\","
                + "\"foreign_landing_url\":\"https://openverse.org/image/x\",\"thumbnail\":\"https://api.openverse.org/t.png\","
                + "\"url\":\"https://api.openverse.org/x.jpg\"}]}";
        List<PublicAsset> assets = PublicAssetSearch.parseOpenverse(json, false);
        assertEquals(1, assets.size());
        PublicAsset asset = assets.get(0);
        assertEquals("OPENVERSE", asset.provider());
        assertEquals("Sunset", asset.title());
        assertEquals("Bob", asset.creator());
        assertEquals("image", asset.mediaType());
        assertTrue(asset.licenseUrl().contains("creativecommons"));
    }

    @Test public void ignoresPagesWithoutImageInfo() throws Exception {
        String json = "{\"query\":{\"pages\":{\"1\":{\"title\":\"File:Broken.png\"}}}}";
        assertTrue(PublicAssetSearch.parseWikimedia(json).isEmpty());
    }
}
