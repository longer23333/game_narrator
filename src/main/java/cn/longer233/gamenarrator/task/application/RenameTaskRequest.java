package cn.longer233.gamenarrator.task.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameTaskRequest(@NotBlank @Size(max = 120) String name) {
}
