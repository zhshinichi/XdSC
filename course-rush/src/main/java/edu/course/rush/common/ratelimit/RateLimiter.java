package edu.course.rush.common.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** 基于 Redis 的固定窗口限流（INCR + EXPIRE，由 Lua 原子完成）。 */
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(new ClassPathResource("lua/rate_limit.lua"), Long.class);
    }

    /** 在 windowSeconds 窗口内，key 最多放行 limit 次。返回 true 放行。 */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long allowed = redisTemplate.execute(
                script,
                List.of(KEY_PREFIX + key),
                String.valueOf(windowSeconds), String.valueOf(limit));
        return allowed != null && allowed == 1L;
    }
}
