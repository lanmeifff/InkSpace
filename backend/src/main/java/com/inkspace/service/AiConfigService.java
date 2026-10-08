package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkspace.common.ai.AiClientConfig;
import com.inkspace.common.util.SecretCipher;
import com.inkspace.config.AiProperties;
import com.inkspace.domain.entity.UserAiConfig;
import com.inkspace.dto.AiConfigRequest;
import com.inkspace.mapper.UserAiConfigMapper;
import com.inkspace.vo.AiConfigVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 用户自带的 AI 接入配置：一人一条，填了就用用户自己的 Key，
 * 没填则回落到服务端 application.yml 的 app.ai.* 默认配置。
 *
 * 每次 AI 调用都要读配置，所以按 userId 在 Redis 里缓存 2 分钟；
 * 保存/删除时直接删缓存。API Key 在缓存里也是密文，读出才解密。
 */
@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);
    private static final String CACHE_KEY = "ai:config:";
    private static final String CIPHER_SEPARATOR = "\u0001";
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final UserAiConfigMapper mapper;
    private final SecretCipher secretCipher;
    private final AiProperties properties;
    private final StringRedisTemplate redis;

    public AiConfigService(UserAiConfigMapper mapper,
                           SecretCipher secretCipher,
                           AiProperties properties,
                           StringRedisTemplate redis) {
        this.mapper = mapper;
        this.secretCipher = secretCipher;
        this.properties = properties;
        this.redis = redis;
    }

    /** 当前用户实际生效的接入参数；Key 缺失时仍返回（由调用方决定怎么报错） */
    public AiClientConfig resolve(Long userId) {
        return toClientConfig(ownConfig(userId));
    }

    public AiConfigVO get(Long userId) {
        UserAiConfig own = ownConfig(userId);
        AiClientConfig effective = toClientConfig(own);
        boolean userKey = effective.hasKey() && own != null;
        return new AiConfigVO(
                own == null ? "" : own.getProviderName(),
                own == null ? "" : own.getUrl(),
                own == null ? "" : mask(decrypt(own.getApiKeyCipher())),
                userKey,
                own == null ? "" : own.getModel(),
                userKey ? "user" : (effective.hasKey() ? "global" : "none"),
                effective.providerName(),
                effective.model());
    }

    /** 用户配了就用用户的，否则回落到服务端 app.ai.* 默认 */
    private AiClientConfig toClientConfig(UserAiConfig own) {
        if (own != null) {
            AiClientConfig config = new AiClientConfig(
                    own.getProviderName(), own.getUrl(), decrypt(own.getApiKeyCipher()), own.getModel());
            if (config.hasKey()) {
                return config;
            }
        }
        return new AiClientConfig("服务端默认", properties.getUrl(), properties.getApiKey(), properties.getModel());
    }

    public AiConfigVO save(Long userId, AiConfigRequest request) {
        UserAiConfig existing = select(userId);
        UserAiConfig config = existing == null ? new UserAiConfig() : existing;
        config.setUserId(userId);
        config.setProviderName(request.getProviderName() == null ? "" : request.getProviderName().trim());
        config.setUrl(request.getUrl().trim());
        config.setModel(request.getModel().trim());
        // 留空 = 不修改已保存的 Key
        if (StringUtils.hasText(request.getApiKey())) {
            config.setApiKeyCipher(secretCipher.encrypt(request.getApiKey().trim()));
        }
        if (existing == null) {
            config.setApiKeyCipher(config.getApiKeyCipher() == null ? "" : config.getApiKeyCipher());
            mapper.insert(config);
        } else {
            mapper.updateById(config);
        }
        evict(userId);
        return get(userId);
    }

    public void delete(Long userId) {
        mapper.delete(new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
        evict(userId);
    }

    private UserAiConfig ownConfig(Long userId) {
        UserAiConfig cached = fromCache(userId);
        if (cached != null) {
            return cached;
        }
        UserAiConfig config = select(userId);
        if (config != null) {
            toCache(config);
        }
        return config;
    }

    private UserAiConfig select(Long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
    }

    /** 缓存里用单条字符串存全部字段，避免为一个对象引入序列化配置 */
    private UserAiConfig fromCache(Long userId) {
        try {
            String raw = redis.opsForValue().get(CACHE_KEY + userId);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            String[] parts = raw.split(CIPHER_SEPARATOR, -1);
            if (parts.length < 4) {
                return null;
            }
            UserAiConfig config = new UserAiConfig();
            config.setProviderName(parts[0]);
            config.setUrl(parts[1]);
            config.setApiKeyCipher(parts[2]);
            config.setModel(parts[3]);
            config.setUserId(userId);
            return config;
        } catch (Exception e) {
            log.warn("读 AI 配置缓存失败 userId={}", userId, e);
            return null;
        }
    }

    private void toCache(UserAiConfig config) {
        try {
            String raw = String.join(CIPHER_SEPARATOR,
                    config.getProviderName() == null ? "" : config.getProviderName(),
                    config.getUrl() == null ? "" : config.getUrl(),
                    config.getApiKeyCipher() == null ? "" : config.getApiKeyCipher(),
                    config.getModel() == null ? "" : config.getModel());
            redis.opsForValue().set(CACHE_KEY + config.getUserId(), raw, CACHE_TTL);
        } catch (Exception e) {
            log.warn("写 AI 配置缓存失败 userId={}", config.getUserId(), e);
        }
    }

    private void evict(Long userId) {
        try {
            redis.delete(CACHE_KEY + userId);
        } catch (Exception e) {
            log.warn("清 AI 配置缓存失败 userId={}", userId, e);
        }
    }

    private String decrypt(String cipher) {
        if (!StringUtils.hasText(cipher)) {
            return "";
        }
        String plain = secretCipher.decrypt(cipher);
        if (plain.isEmpty()) {
            log.warn("AI Key 解密失败，可能是 jwt.secret 变更");
        }
        return plain;
    }

    /** 只回显首尾，中间打码：sk-a****wxyz */
    private static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
