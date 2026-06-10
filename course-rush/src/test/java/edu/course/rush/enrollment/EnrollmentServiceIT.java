package edu.course.rush.enrollment;

import edu.course.rush.batch.BatchService;
import edu.course.rush.batch.EnrollBatch;
import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.course.Course;
import edu.course.rush.course.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class EnrollmentServiceIT {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private BatchService batchService;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private RedisStockService stockService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        enrollmentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private Long createOpenCourse(int capacity) {
        Instant now = Instant.now();
        EnrollBatch batch = batchService.create("开放批次",
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        Course course = courseService.create("操作系统", "张老师", "周一", capacity, batch.getId());
        enrollmentService.preheat(course.getId());
        return course.getId();
    }

    private Long createClosedCourse(int capacity) {
        Instant now = Instant.now();
        EnrollBatch batch = batchService.create("已关闭批次",
                now.minus(3, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        Course course = courseService.create("已结束课程", "李老师", "周二", capacity, batch.getId());
        enrollmentService.preheat(course.getId());
        return course.getId();
    }

    @Test
    void enrollPersistsEnrollmentAndDecrementsStock() {
        Long courseId = createOpenCourse(5);

        EnrollAck ack = enrollmentService.enroll(courseId, 100L);

        assertThat(ack.status()).isEqualTo("ENROLLED"); // 测试默认同步落库
        assertThat(enrollmentRepository.countByCourseId(courseId)).isEqualTo(1);
        assertThat(stockService.getStock(courseId)).isEqualTo(4);
    }

    @Test
    void enrollingSameCourseTwiceThrows() {
        Long courseId = createOpenCourse(5);
        enrollmentService.enroll(courseId, 100L);

        assertThatThrownBy(() -> enrollmentService.enroll(courseId, 100L))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void soldOutThrows() {
        Long courseId = createOpenCourse(1);
        enrollmentService.enroll(courseId, 100L);

        assertThatThrownBy(() -> enrollmentService.enroll(courseId, 101L))
                .isInstanceOf(SoldOutException.class);
    }

    @Test
    void enrollOutsideWindowThrows() {
        Long courseId = createClosedCourse(5);

        assertThatThrownBy(() -> enrollmentService.enroll(courseId, 100L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void dropReleasesSeatAndAllowsAnotherToEnroll() {
        Long courseId = createOpenCourse(1);
        enrollmentService.enroll(courseId, 100L);
        assertThat(stockService.getStock(courseId)).isEqualTo(0);

        enrollmentService.drop(courseId, 100L);

        assertThat(enrollmentRepository.countByCourseId(courseId)).isEqualTo(0);
        assertThat(stockService.getStock(courseId)).isEqualTo(1);
        // 名额释放后，别人可以抢到
        assertThat(enrollmentService.enroll(courseId, 101L).status()).isEqualTo("ENROLLED");
    }

    @Test
    void dropWhenNotEnrolledThrows() {
        Long courseId = createOpenCourse(5);

        assertThatThrownBy(() -> enrollmentService.drop(courseId, 100L))
                .isInstanceOf(edu.course.rush.common.error.ResourceNotFoundException.class);
    }

    @Test
    void courseStatsReflectsEnrollments() {
        Long courseId = createOpenCourse(5);
        enrollmentService.enroll(courseId, 100L);
        enrollmentService.enroll(courseId, 101L);

        CourseStats stats = enrollmentService.courseStats(courseId);

        assertThat(stats.capacity()).isEqualTo(5);
        assertThat(stats.enrolled()).isEqualTo(2);
        assertThat(stats.remaining()).isEqualTo(3);
    }

    /** 核心：service 层连数据库一起验证不超卖。 */
    @Test
    void neverOversellsUnderConcurrency() throws InterruptedException {
        int capacity = 20;
        int students = 200;
        Long courseId = createOpenCourse(capacity);

        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(students);

        for (int i = 0; i < students; i++) {
            long studentId = 1000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    enrollmentService.enroll(courseId, studentId);
                } catch (Exception ignored) {
                    // 抢不到/已满属正常
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(enrollmentRepository.countByCourseId(courseId)).isEqualTo(capacity);
        assertThat(stockService.getStock(courseId)).isEqualTo(0);
    }
}
