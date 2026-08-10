package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracted Bilibili article or opus content, kept for display only.
 */
public final class BilibiliArticle {
    private final String title, content, sourceUrl;
    private final List<String> imageUrls;

    public BilibiliArticle(String title, String content, String sourceUrl, List<String> imageUrls) {
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
    }

    public String title() { return title; }
    public String content() { return content; }
    public String sourceUrl() { return sourceUrl; }
    public List<String> imageUrls() { return new ArrayList<>(imageUrls); }
}
