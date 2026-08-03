package cn.longer233.gamenarrator.asset;

import java.util.List;

public record AssetTagUpdateRequest(List<String> add, List<String> remove) {
}
