package edu.course.rush.enrollment;

import edu.course.rush.batch.BatchService;
import edu.course.rush.batch.EnrollBatch;
import edu.course.rush.course.Course;
import edu.course.rush.course.CourseService;
import edu.course.rush.enrollment.mq.EnrollEvent;
import edu.course.rush.enrollment.mq.EnrollEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(properties = {
        "app.enroll.persist-mode=async",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.ack-mode=manual"
})
@EmbeddedKafka(topics = "enroll-events", partitions = 1)
@Testcontainers
class AsyncEnrollmentIT {

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
    private EnrollEventProducer producer;

    private Long createOpenPreheatedCourse(int capacity) {
        Instant now = Instant.now();
        EnrollBatch batch = batchService.create("开放",
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        Course course = courseService.create("操作系统", "张老师", "周一", capacity, batch.getId());
        enrollmentService.preheat(course.getId());
        return course.getId();
    }

    private Callable<Boolean> countEquals(Long courseId, long expected) {
        return () -> enrollmentRepository.countByCourseId(courseId) == expected;
    }

    @Test
    void enrollReturnsPendingAndConsumerPersistsEventually() {
        Long courseId = createOpenPreheatedCourse(5);

        EnrollAck ack = enrollmentService.enroll(courseId, 100L);

        assertThat(ack.status()).isEqualTo("PENDING");
        // 库存在 Redis 闸门处已同步扣减
        assertThat(stockService.getStock(courseId)).isEqualTo(4);
        // 落库由 Kafka 消费者异步完成
        await().atMost(10, SECONDS).until(countEquals(courseId, 1));
    }

    @Test
    void duplicateEventIsPersistedOnlyOnce() {
        Long courseId = createOpenPreheatedCourse(5);
        EnrollEvent event = new EnrollEvent(200L, courseId, Instant.now().toEpochMilli());

        producer.publish(event);
        producer.publish(event); // 重复投递

        await().atMost(10, SECONDS).until(countEquals(courseId, 1));
        // 再等一会，确认不会出现第二条（幂等）
        await().during(2, SECONDS).atMost(5, SECONDS).until(countEquals(courseId, 1));
    }
}
