package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.Pattern;

public record MoveStoryboardSegmentRequest(
        @Pattern(regexp = "UP|DOWN") String direction
) {
}
