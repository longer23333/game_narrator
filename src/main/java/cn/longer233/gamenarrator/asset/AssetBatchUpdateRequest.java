package cn.longer233.gamenarrator.asset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AssetBatchUpdateRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> assetIds,
        Boolean favorite,
        Boolean archived,
        @Size(max = 20) List<@Size(min = 1, max = 100) String> addTags,
        boolean delete
) {
}
