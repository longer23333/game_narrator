package cn.longer233.gamenarrator.editor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRevisionRequest(@NotBlank @Size(max = 100) String label) { }
