package cn.longer233.gamenarrator.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateGameEventRequest(
        @NotBlank @Size(max = 40) String eventType,
        @NotBlank @Size(max = 500) String description,
        @Min(0) @Max(100) int importance,
        @NotBlank @Pattern(regexp = "AI_SUGGESTED|CONFIRMED|NEEDS_REVIEW") String confirmationStatus
) {
}
