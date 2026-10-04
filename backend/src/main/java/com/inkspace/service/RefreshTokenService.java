package com.inkspace.service;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Refresh Token 会话管理（存 Redis，可吊销）。
 *
 * 令牌格式：{userId}.{32 字节随机 hex}
 * Redis 结构：Hash  auth:refresh:{userId}   field = sha256(随机部分)   value = 设备标识
 * 一个 key 管一个用户的所有设备，登出删对应 field，全部下线直接 DEL 整个 key。
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final StringRedisTemplate redis;
    private final Duration refreshTtl;

    public RefreshTokenService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.refreshTtl = Duration.ofDays(properties.getRefreshTtlDays());
    }

    /** 签发新 Refresh Token（同一用户多设备会累加 field） */
    public String issue(Long userId) {
        String randomPart = randomHex(32);
        String key = KEY_PREFIX + userId;
        redis.opsForHash().put(key, sha256Hex(randomPart), "web");
        redis.expire(key, refreshTtl);
        return userId + "." + randomPart;
    }

    /** 轮换：校验旧令牌并作废，返回新令牌。旧令牌再次出现视为重放，吊销该用户全部会话。 */
    public Rotation rotate(String token) {
        ParsedToken parsed = parse(token);
        String key = KEY_PREFIX + parsed.userId();
        Long removed = redis.opsForHash().delete(key, sha256Hex(parsed.randomPart()));
        if (removed == null || removed == 0) {
            // 令牌不在会话集合中：可能已被使用过的重放，安全起见全部下线
            redis.delete(key);
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new Rotation(parsed.userId(), issue(parsed.userId()));
    }

    /** 吊销该用户全部会话（改密、风控时使用）：删掉整个用户 key 即可 */
    public void revokeAll(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }

    /** 登出：删掉该设备的会话（幂等，令牌非法也不报错） */
    public void revoke(String token) {
        try {
            ParsedToken parsed = parse(token);
            String key = KEY_PREFIX + parsed.userId();
            redis.opsForHash().delete(key, sha256Hex(parsed.randomPart()));
            Long size = redis.opsForHash().size(key);
            if (size != null && size == 0) {
                redis.delete(key);
            }
        } catch (BizException ignored) {
            // 登出是幂等操作：令牌无效时直接返回成功
        }
    }

    private ParsedToken parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        try {
            return new ParsedToken(Long.valueOf(token.substring(0, dot)), token.substring(dot + 1));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HEX.formatHex(buffer);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private record ParsedToken(Long userId, String randomPart) {
    }

    /** 轮换结果：用户 id + 新 Refresh Token */
    public record Rotation(Long userId, String refreshToken) {
    }
}
