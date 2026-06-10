package edu.course.rush.enrollment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
class RedisStockServiceTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RedisStockService stockService;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        stockService = new RedisStockService(redisTemplate);
    }

    @Test
    void successfulEnrollDecrementsStock() {
        stockService.preheat(1L, 5);

        EnrollOutcome outcome = stockService.tryEnroll(1L, 100L);

        assertThat(outcome).isEqualTo(EnrollOutcome.SUCCESS);
        assertThat(stockService.getStock(1L)).isEqualTo(4);
    }

    @Test
    void enrollWithoutPreheatReturnsNotInitialized() {
        assertThat(stockService.tryEnroll(2L, 100L)).isEqualTo(EnrollOutcome.NOT_INITIALIZED);
    }

    @Test
    void sameStudentEnrollingTwiceIsRejected() {
        stockService.preheat(3L, 5);

        assertThat(stockService.tryEnroll(3L, 100L)).isEqualTo(EnrollOutcome.SUCCESS);
        assertThat(stockService.tryEnroll(3L, 100L)).isEqualTo(EnrollOutcome.ALREADY_ENROLLED);
        assertThat(stockService.getStock(3L)).isEqualTo(4); // 第二次不应再扣减
    }

    @Test
    void soldOutWhenStockExhausted() {
        stockService.preheat(4L, 1);

        assertThat(stockService.tryEnroll(4L, 100L)).isEqualTo(EnrollOutcome.SUCCESS);
        assertThat(stockService.tryEnroll(4L, 101L)).isEqualTo(EnrollOutcome.SOLD_OUT);
        assertThat(stockService.getStock(4L)).isEqualTo(0);
    }

    @Test
    void rollbackRestoresStockAndAllowsReEnroll() {
        stockService.preheat(5L, 1);
        stockService.tryEnroll(5L, 100L);

        stockService.rollback(5L, 100L);

        assertThat(stockService.getStock(5L)).isEqualTo(1);
        assertThat(stockService.tryEnroll(5L, 100L)).isEqualTo(EnrollOutcome.SUCCESS);
    }

    /** 核心：高并发下绝不超卖。容量 K，N 个不同学生同时抢，恰好 K 人成功。 */
    @Test
    void neverOversellsUnderConcurrency() throws InterruptedException {
        int capacity = 20;
        int students = 200;
        stockService.preheat(9L, capacity);

        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(students);
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentHashMap<Long, Boolean> winners = new ConcurrentHashMap<>();

        for (int i = 0; i < students; i++) {
            long studentId = 1000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (stockService.tryEnroll(9L, studentId) == EnrollOutcome.SUCCESS) {
                        successCount.incrementAndGet();
                        winners.put(studentId, true);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(capacity);
        assertThat(winners).hasSize(capacity);
        assertThat(stockService.getStock(9L)).isEqualTo(0);
    }
}
