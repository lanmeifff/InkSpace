package com.inkspace.common.ratelimit;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录限流：按来源 IP 计数（Redis INCR + EXPIRE）。
 */
@Component
public class LoginRateLimiter {

    private static final String KEY_PREFIX = "rate:login:";
    private static final int LIMIT_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 每次登录尝试前调用；超限抛 429 */
    public void check(String ip) {
        String key = KEY_PREFIX + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        if (count != null && count > LIMIT_PER_WINDOW) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
