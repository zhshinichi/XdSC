package edu.course.rush.enrollment;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程库存的 Redis 快速路径：预热、原子抢课(防重+扣减)、回补。
 * 抢课的"不超卖"由 Lua 脚本的原子性保证。
 */
@Service
public class RedisStockService {

    private static final String STOCK_KEY_PREFIX = "course:stock:";
    private static final String ENROLLED_SET_PREFIX = "course:enrolled:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tryEnrollScript;
    private final RedisScript<Long> rollbackScript;

    public RedisStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tryEnrollScript = RedisScript.of(new ClassPathResource("lua/try_enroll.lua"), Long.class);
        this.rollbackScript = RedisScript.of(new ClassPathResource("lua/rollback_enroll.lua"), Long.class);
    }

    /** 预热：把课程容量写入 Redis 库存。 */
    public void preheat(Long courseId, int capacity) {
        redisTemplate.opsForValue().set(stockKey(courseId), String.valueOf(capacity));
    }

    /** 仅当库存 key 不存在时预热（并发安全，只有第一个调用生效）。 */
    public void preheatIfAbsent(Long courseId, int capacity) {
        redisTemplate.opsForValue().setIfAbsent(stockKey(courseId), String.valueOf(capacity));
    }

    /** 当前剩余名额；未预热返回 -1。 */
    public int getStock(Long courseId) {
        String v = redisTemplate.opsForValue().get(stockKey(courseId));
        return v == null ? -1 : Integer.parseInt(v);
    }

    /** 原子抢课：防重复 + 扣减库存。 */
    public EnrollOutcome tryEnroll(Long courseId, Long studentId) {
        Long code = redisTemplate.execute(
                tryEnrollScript,
                List.of(stockKey(courseId), enrolledSetKey(courseId)),
                String.valueOf(studentId));
        long c = code == null ? -2 : code;
        if (c == -2) {
            return EnrollOutcome.NOT_INITIALIZED;
        }
        if (c == -3) {
            return EnrollOutcome.ALREADY_ENROLLED;
        }
        if (c == -1) {
            return EnrollOutcome.SOLD_OUT;
        }
        return EnrollOutcome.SUCCESS;
    }

    /** 回补一个名额并移除已选标记（退课/失败补偿）。 */
    public void rollback(Long courseId, Long studentId) {
        redisTemplate.execute(
                rollbackScript,
                List.of(stockKey(courseId), enrolledSetKey(courseId)),
                String.valueOf(studentId));
    }

    private String stockKey(Long courseId) {
        return STOCK_KEY_PREFIX + courseId;
    }

    private String enrolledSetKey(Long courseId) {
        return ENROLLED_SET_PREFIX + courseId;
    }
}
