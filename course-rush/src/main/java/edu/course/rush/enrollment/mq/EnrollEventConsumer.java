package edu.course.rush.enrollment.mq;

import edu.course.rush.enrollment.Enrollment;
import edu.course.rush.enrollment.EnrollmentRepository;
import edu.course.rush.enrollment.EnrollmentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 消费"选课成功"事件并落库。
 * 幂等：先查 (studentId, courseId) 是否已存在，并以 DB 唯一索引兜底；
 * 处理成功后手动提交 offset，实现 at-least-once 且不重复落库。
 */
@Component
@ConditionalOnProperty(name = "app.enroll.persist-mode", havingValue = "async")
public class EnrollEventConsumer {

    private final EnrollmentRepository enrollmentRepository;
    /** 压测用：模拟"远程/高负载数据库"的单次写入延迟（毫秒），默认 0。异步模式下该延迟落在后台消费者线程，不阻塞抢课请求。 */
    private final long simWriteLatencyMs;

    public EnrollEventConsumer(EnrollmentRepository enrollmentRepository,
                               @Value("${app.enroll.sim-write-latency-ms:0}") long simWriteLatencyMs) {
        this.enrollmentRepository = enrollmentRepository;
        this.simWriteLatencyMs = simWriteLatencyMs;
    }

    @KafkaListener(topics = "${app.enroll.topic}",
            groupId = "${spring.kafka.consumer.group-id:enroll-consumer}")
    @Transactional
    public void onMessage(EnrollEvent event, Acknowledgment ack) {
        persistIdempotent(event);
        ack.acknowledge();
    }

    private void persistIdempotent(EnrollEvent event) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(event.studentId(), event.courseId())) {
            return; // 已落库，幂等跳过
        }
        try {
            if (simWriteLatencyMs > 0) {
                try { Thread.sleep(simWriteLatencyMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setStudentId(event.studentId());
            enrollment.setCourseId(event.courseId());
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setCreatedAt(Instant.ofEpochMilli(event.createdAtEpochMs()));
            enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException dup) {
            // 并发下唯一索引兜底：重复消息忽略
        }
    }
}
