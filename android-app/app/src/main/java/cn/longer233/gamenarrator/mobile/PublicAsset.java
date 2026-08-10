package cn.longer233.gamenarrator.mobile;

/**
 * A discovered public-source asset with its license metadata.
 */
public final class PublicAsset {
    private final String provider, title, creator, license, licenseUrl, pageUrl, thumbnailUrl, directUrl, mediaType;

    public PublicAsset(String provider, String title, String creator, String license, String licenseUrl,
                       String pageUrl, String thumbnailUrl, String directUrl, String mediaType) {
        this.provider = provider == null ? "" : provider;
        this.title = title == null ? "" : title;
        this.creator = creator == null ? "" : creator;
        this.license = license == null ? "" : license;
        this.licenseUrl = licenseUrl == null ? "" : licenseUrl;
        this.pageUrl = pageUrl == null ? "" : pageUrl;
        this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
        this.directUrl = directUrl == null ? "" : directUrl;
        this.mediaType = mediaType == null ? "other" : mediaType;
    }

    public String provider() { return provider; }
    public String title() { return title; }
    public String creator() { return creator; }
    public String license() { return license; }
    public String licenseUrl() { return licenseUrl; }
    public String pageUrl() { return pageUrl; }
    public String thumbnailUrl() { return thumbnailUrl; }
    public String directUrl() { return directUrl; }
    public String mediaType() { return mediaType; }
}
