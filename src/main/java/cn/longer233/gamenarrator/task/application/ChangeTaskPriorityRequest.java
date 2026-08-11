package cn.longer233.gamenarrator.task.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChangeTaskPriorityRequest(@Min(-100) @Max(100) int priority) { }
