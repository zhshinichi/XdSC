package edu.course.rush.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBatchRequest(
        @NotBlank String name,
        @NotNull Instant openAt,
        @NotNull Instant closeAt) {
}
