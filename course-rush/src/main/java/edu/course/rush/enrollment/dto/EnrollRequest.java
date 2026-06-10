package edu.course.rush.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollRequest(@NotNull Long courseId) {
}
