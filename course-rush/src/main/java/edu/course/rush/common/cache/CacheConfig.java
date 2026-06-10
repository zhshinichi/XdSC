package edu.course.rush.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 进程内缓存配置（Caffeine）。
 *
 * <p>抢课热路径上，课程与批次元数据是<b>只读、稳定</b>的小数据：每个选课请求原本都要查
 * {@code course} 与 {@code enroll_batch} 两张表，在高并发下被 Hikari 连接池(10)串行化，
 * 成为吞吐瓶颈。这里用 Caffeine 作为一级缓存（无网络、无序列化）把它们缓存起来，
 * 使热路径只剩 Redis 原子扣减，DB 读降为 0。
 *
 * <p>库存仍然放 Redis（需要跨请求原子性），二者构成多级缓存：Caffeine(L1，元数据) + Redis(库存)。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COURSES = "courses";
    public static final String BATCHES = "batches";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(COURSES, BATCHES);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return manager;
    }
}
