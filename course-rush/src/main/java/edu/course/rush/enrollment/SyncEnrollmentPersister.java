package edu.course.rush.enrollment;

import edu.course.rush.enrollment.mq.EnrollEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 同步落库（默认/降级）：抢课成功后立即写库，DB 唯一索引兜底防重。 */
@Component
@ConditionalOnProperty(name = "app.enroll.persist-mode", havingValue = "sync", matchIfMissing = true)
public class SyncEnrollmentPersister implements EnrollmentPersister {

    private final EnrollmentRepository enrollmentRepository;
    private final RedisStockService stockService;
    /** 压测用：模拟"远程/高负载数据库"的单次写入延迟（毫秒），默认 0 不开启。同步模式下该延迟阻塞请求线程。 */
    private final long simWriteLatencyMs;

    public SyncEnrollmentPersister(EnrollmentRepository enrollmentRepository,
                                   RedisStockService stockService,
                                   @Value("${app.enroll.sim-write-latency-ms:0}") long simWriteLatencyMs) {
        this.enrollmentRepository = enrollmentRepository;
        this.stockService = stockService;
        this.simWriteLatencyMs = simWriteLatencyMs;
    }

    @Override
    @Transactional
    public EnrollAck persist(EnrollEvent event) {
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
            return new EnrollAck(event.studentId(), event.courseId(), "ENROLLED");
        } catch (DataIntegrityViolationException dup) {
            stockService.rollback(event.courseId(), event.studentId());
            throw new AlreadyEnrolledException("你已选过该课程");
        }
    }
}
