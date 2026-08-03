package cn.longer233.gamenarrator.script;

import jakarta.validation.constraints.Size;

public record RegenerateScriptSegmentRequest(@Size(max = 500) String instruction) {
}
