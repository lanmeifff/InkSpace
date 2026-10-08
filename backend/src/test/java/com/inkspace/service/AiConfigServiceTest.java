package com.inkspace.service;

import com.inkspace.common.ai.AiClientConfig;
import com.inkspace.common.util.SecretCipher;
import com.inkspace.config.AiProperties;
import com.inkspace.config.JwtProperties;
import com.inkspace.domain.entity.UserAiConfig;
import com.inkspace.mapper.UserAiConfigMapper;
import com.inkspace.vo.AiConfigVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「用户自己配了 Key 就不该走 mock」这条规则的回归测试。
 *
 * 背景：以前 app.ai.mock=true 时 Spring 只注册 MockLlmClient 这一个 bean，
 * 用户在设置里填的真实配置被完全绕过、界面上又没有任何提示，
 * 表现就是"无论什么文章，摘要和回答都是同一段固定文字"。实测踩过，
 * 所以把这条规则钉死在测试里：mock 只能是"服务端开了 mock 且用户没配 Key"时的兜底。
 */
class AiConfigServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** 模拟库里那条配置；子类通过它回放，避免连数据库 */
    private UserAiConfig owned;

    private static SecretCipher cipher() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        return new SecretCipher(properties);
    }

    private AiConfigService service(AiProperties properties) {
        // mapper 用 Mockito，避免手写实现 BaseMapper 的一堆抽象方法；
        // select 已被下面重写，所以不会真的碰数据库。redis 也重写掉了。
        UserAiConfigMapper mapper = Mockito.mock(UserAiConfigMapper.class);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        return new AiConfigService(mapper, cipher(), properties, redis) {
            @Override
            UserAiConfig select(Long userId) {
                return owned;
            }

            @Override
            void evict(Long userId) {
                // 测试里不需要清缓存
            }
        };
    }

    @Test
    void userKeyWinsOverServerMockMode() {
        AiProperties properties = new AiProperties();
        properties.setMock(true);                 // 服务端开着 mock
        properties.setApiKey("");                 // 但服务端自己没有 Key
        properties.setModel("server-model");

        owned = userConfig("https://api.deepseek.com/v1/chat/completions", "用户自己的Key", "deepseek-flash");

        AiClientConfig config = service(properties).resolve(1L);
        assertFalse(config.mock(), "用户配了 Key 就不该走 mock —— 否则填的配置等于没填");
        assertEquals("deepseek-flash", config.model());
        assertTrue(config.hasKey());
    }

    @Test
    void mockOnlyAppliesWhenUserHasNoConfig() {
        AiProperties properties = new AiProperties();
        properties.setMock(true);
        properties.setApiKey("");

        owned = null;

        AiClientConfig config = service(properties).resolve(1L);
        assertTrue(config.mock(), "服务端开了 mock 且用户没配 Key 时才该走 mock");
    }

    @Test
    void serverKeyTurnsOffMockEvenWithoutUserConfig() {
        AiProperties properties = new AiProperties();
        properties.setMock(true);
        properties.setApiKey("服务端自己的Key");    // 服务端配了真 Key
        properties.setModel("server-model");

        owned = null;

        AiClientConfig config = service(properties).resolve(1L);
        assertFalse(config.mock(), "服务端有 Key 时应当真调，mock 只是没 Key 时的兜底");
        assertEquals("server-model", config.model());
    }

    @Test
    void configViewTellsFrontendWhetherItIsMock() {
        AiProperties properties = new AiProperties();
        properties.setMock(true);
        properties.setApiKey("");
        owned = null;

        AiConfigVO vo = service(properties).get(1L);
        assertTrue(vo.mock(), "回显要给前端一个 mock 标记，界面才能提示'当前是假数据'");
        assertEquals("none", vo.source());
    }

    private static UserAiConfig userConfig(String url, String plainKey, String model) {
        UserAiConfig config = new UserAiConfig();
        config.setUserId(1L);
        config.setProviderName("deepseek");
        config.setUrl(url);
        config.setModel(model);
        config.setApiKeyCipher(cipher().encrypt(plainKey));
        return config;
    }
}
