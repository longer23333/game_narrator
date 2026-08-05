package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateScriptSegmentRequest(
        @NotNull @Size(max = 500) String narration,
        @Size(max = 500) String subtitle,
        @Size(max = 200) String effectCue
) {
}
