package edu.course.rush.enrollment.dto;

import edu.course.rush.enrollment.Enrollment;

import java.time.Instant;

public record EnrollmentResponse(
        Long id, Long studentId, Long courseId, String status, Instant createdAt) {
    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getStudentId(), e.getCourseId(),
                e.getStatus().name(), e.getCreatedAt());
    }
}
