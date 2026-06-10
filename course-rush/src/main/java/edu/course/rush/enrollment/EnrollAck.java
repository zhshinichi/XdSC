package edu.course.rush.enrollment;

/** 抢课受理结果。status: ENROLLED(同步已落库) 或 PENDING(异步处理中)。 */
public record EnrollAck(Long studentId, Long courseId, String status) {
}
