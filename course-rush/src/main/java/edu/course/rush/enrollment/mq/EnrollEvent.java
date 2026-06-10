package edu.course.rush.enrollment.mq;

/** "选课成功"事件：经 Kafka 异步落库。(studentId, courseId) 天然作为幂等键。 */
public record EnrollEvent(Long studentId, Long courseId, long createdAtEpochMs) {
}
