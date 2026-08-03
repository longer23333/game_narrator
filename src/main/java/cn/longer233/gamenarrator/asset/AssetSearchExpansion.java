package cn.longer233.gamenarrator.asset;

import java.util.List;

public record AssetSearchExpansion(String originalQuery, String providerQuery, List<String> chineseTags) {
}
