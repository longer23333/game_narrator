package cn.longer233.gamenarrator.creative;
import jakarta.validation.constraints.NotBlank;
public record MemeCandidateRequest(@NotBlank String context, String caption, String sourceUrl, String sourceAttribution) {}
