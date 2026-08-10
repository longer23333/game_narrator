package cn.longer233.gamenarrator.community;

import java.util.List;

public record PublishCommunityResourceRequest(String authorName, String licenseCode, List<String> tags) { }
