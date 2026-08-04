package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManualScriptReviewRequest(
        @NotNull @Pattern(regexp = "APPROVED|NEEDS_CHANGES") String status,
        @Size(max = 500) String note) {
}
