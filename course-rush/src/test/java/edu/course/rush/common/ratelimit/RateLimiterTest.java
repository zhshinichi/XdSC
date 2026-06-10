package edu.course.rush.common.ratelimit;

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

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
class RateLimiterTest {

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

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        rateLimiter = new RateLimiter(redisTemplate);
    }

    @Test
    void allowsUpToLimitThenRejects() {
        assertThat(rateLimiter.tryAcquire("u1", 3, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("u1", 3, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("u1", 3, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("u1", 3, 60)).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        assertThat(rateLimiter.tryAcquire("a", 1, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("a", 1, 60)).isFalse();
        assertThat(rateLimiter.tryAcquire("b", 1, 60)).isTrue();
    }
}
