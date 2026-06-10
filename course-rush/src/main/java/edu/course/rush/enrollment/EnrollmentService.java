package edu.course.rush.enrollment;

import edu.course.rush.batch.BatchService;
import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ResourceNotFoundException;
import edu.course.rush.course.Course;
import edu.course.rush.course.CourseService;
import edu.course.rush.enrollment.mq.EnrollEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 抢课链路编排：
 * 校验课程与时间窗口 -> Redis 原子抢课(防重+扣减) -> 交由落库策略持久化。
 * 落库策略({@link EnrollmentPersister})可为同步写库或 Kafka 异步落库，由配置切换。
 */
@Service
public class EnrollmentService {

    private final CourseService courseService;
    private final BatchService batchService;
    private final RedisStockService stockService;
    private final EnrollmentPersister persister;
    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentService(CourseService courseService,
                             BatchService batchService,
                             RedisStockService stockService,
                             EnrollmentPersister persister,
                             EnrollmentRepository enrollmentRepository) {
        this.courseService = courseService;
        this.batchService = batchService;
        this.stockService = stockService;
        this.persister = persister;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** 缓存预热：把课程容量加载进 Redis（抢课开放前调用）。 */
    public void preheat(Long courseId) {
        Course course = courseService.getById(courseId);
        stockService.preheat(courseId, course.getCapacity());
    }

    public EnrollAck enroll(Long courseId, Long studentId) {
        Course course = courseService.getById(courseId);
        // 跨 bean 调用 getById（走 @Cacheable 代理），避免 batchService.isOpen 内部自调用绕过缓存；
        // 窗口判断用缓存到的批次实体 + 当前时间实时计算，热路径不再读 DB。
        if (!batchService.getById(course.getBatchId()).isOpenAt(Instant.now())) {
            throw new BadRequestException("不在抢课时间窗口内");
        }

        EnrollOutcome outcome = stockService.tryEnroll(courseId, studentId);
        if (outcome == EnrollOutcome.NOT_INITIALIZED) {
            stockService.preheatIfAbsent(courseId, course.getCapacity());
            outcome = stockService.tryEnroll(courseId, studentId);
        }

        switch (outcome) {
            case ALREADY_ENROLLED -> throw new AlreadyEnrolledException("你已选过该课程");
            case SOLD_OUT -> throw new SoldOutException("该课程名额已抢完");
            case NOT_INITIALIZED -> throw new BadRequestException("课程库存未初始化");
            case SUCCESS -> { /* 继续落库 */ }
        }

        EnrollEvent event = new EnrollEvent(studentId, courseId, Instant.now().toEpochMilli());
        return persister.persist(event);
    }

    /** 退课：删除选课记录并回补一个名额。 */
    @Transactional
    public void drop(Long courseId, Long studentId) {
        Enrollment enrollment = enrollmentRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("未选该课程，无法退课"));
        enrollmentRepository.delete(enrollment);
        stockService.rollback(courseId, studentId);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> myEnrollments(Long studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public CourseStats courseStats(Long courseId) {
        Course course = courseService.getById(courseId);
        int enrolled = (int) enrollmentRepository.countByCourseId(courseId);
        return new CourseStats(courseId, course.getCapacity(), enrolled,
                course.getCapacity() - enrolled);
    }
}
