package edu.course.rush.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCourseRequest(
        @NotBlank String name,
        @NotBlank String teacher,
        String timeSlot,
        @Positive int capacity,
        @NotNull Long batchId) {
}
