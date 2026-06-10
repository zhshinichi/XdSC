package edu.course.rush.batch.dto;

import edu.course.rush.batch.EnrollBatch;

import java.time.Instant;

public record BatchResponse(Long id, String name, Instant openAt, Instant closeAt) {
    public static BatchResponse from(EnrollBatch b) {
        return new BatchResponse(b.getId(), b.getName(), b.getOpenAt(), b.getCloseAt());
    }
}
