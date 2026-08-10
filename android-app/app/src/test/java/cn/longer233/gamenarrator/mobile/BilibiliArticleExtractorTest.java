package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BilibiliArticleExtractorTest {
    @Test public void extractsArticleIdFromReadAndOpusUrls() {
        assertEquals("123456", BilibiliArticleExtractor.articleId("https://www.bilibili.com/read/cv123456"));
        assertEquals("789", BilibiliArticleExtractor.articleId("https://www.bilibili.com/opus/789"));
        assertNull(BilibiliArticleExtractor.articleId("https://example.com/video/1"));
    }

    @Test public void parsesArticleJsonWithImages() throws Exception {
        String json = "{\"code\":0,\"data\":{\"title\":\"测试专栏\",\"content\":\"<p>第一段</p><p>第二段</p>\","
                + "\"origin_image_urls\":[\"https://img.example/1.jpg\"]}}";
        BilibiliArticle article = BilibiliArticleExtractor.parseArticle(json, "https://www.bilibili.com/read/cv1");
        assertEquals("测试专栏", article.title());
        assertEquals("第一段 第二段", article.content());
        assertEquals(1, article.imageUrls().size());
    }

    @Test public void parsesOpusJsonWithImageObjects() throws Exception {
        String json = "{\"code\":0,\"data\":{\"item\":{\"content\":\"<p>动态正文</p>\","
                + "\"images\":[{\"url\":\"https://img.example/2.jpg\"},{\"url\":\"https://img.example/3.jpg\"}]}}}";
        BilibiliArticle article = BilibiliArticleExtractor.parseOpus(json, "https://www.bilibili.com/opus/9");
        assertEquals("动态正文", article.content());
        assertEquals(2, article.imageUrls().size());
        assertTrue(article.imageUrls().contains("https://img.example/3.jpg"));
    }
}
