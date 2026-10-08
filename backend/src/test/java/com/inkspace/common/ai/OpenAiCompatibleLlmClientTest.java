package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AiProperties;
import com.inkspace.service.AiConfigService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容客户端的按用户解析 + HTTP + SSE 解析自检。
 * 需要一个本地桩服务（tools/llm-stub.cjs，监听 127.0.0.1:18123）：
 * 它在 Key 不等于 sk-stub-key-0001 时返回 401，用来验证错误 Key 的报错分支。
 * 桩服务没起时本类直接跳过，不会让 mvn test 变红。
 */
class OpenAiCompatibleLlmClientTest {

    private static final String STUB_URL = "http://127.0.0.1:18123/v1/chat/completions";
    private static final String STUB_KEY = "sk-stub-key-0001";

    @Test
    void resolvesConfigPerUserAndTalksToTheConfiguredEndpoint() {
        if (!stubUp()) {
            return;
        }
        AiProperties properties = new AiProperties();
        properties.setUrl(STUB_URL);
        properties.setApiKey(STUB_KEY);
        properties.setModel("stub-global-model");
        StubConfigService configs = new StubConfigService(properties);
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(configs, new ObjectMapper(), properties);

        // 用户自带配置：模型与 Key 都该用用户自己的
        configs.user = new AiClientConfig("用户自带", STUB_URL, STUB_KEY, "stub-user-model");
        LlmResult result = client.chat(1L, List.of(LlmMessage.user("ping")), false);
        assertEquals("stub-ok:stub-user-model", result.content());
        assertEquals(7, result.promptTokens());
        assertEquals(3, result.completionTokens());
        assertEquals("stub-user-model", client.currentModel(1L));

        // SSE 流式：每段 delta 都要拼回来
        StringBuilder streamed = new StringBuilder();
        List<String> deltas = new ArrayList<>();
        client.chatStream(1L, List.of(LlmMessage.user("ping")), delta -> {
            deltas.add(delta);
            streamed.append(delta);
        });
        assertEquals("你好，这是流式回答", streamed.toString());
        assertTrue(deltas.size() > 1, "应当分多片推送");

        // 用户没配：回落到服务端默认
        configs.user = null;
        assertEquals("stub-global-model", client.currentModel(9L));

        // 两边都没 Key：报"未配置"，不发请求
        configs.globalKey = "";
        BizException notConfigured = assertThrows(BizException.class,
                () -> client.chat(9L, List.of(LlmMessage.user("ping")), false));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, notConfigured.getErrorCode());

        // Key 错误：桩服务 401 → 调用失败
        configs.globalKey = "sk-wrong";
        BizException failed = assertThrows(BizException.class,
                () -> client.chat(9L, List.of(LlmMessage.user("ping")), false));
        assertEquals(ErrorCode.AI_CALL_FAILED, failed.getErrorCode());
    }

    private static boolean stubUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 18123), 500);
            return true;
        } catch (Exception e) {
            System.out.println("[skip] 桩服务未启动，跳过 LLM 客户端自检（node tools/llm-stub.cjs）");
            return false;
        }
    }

    /** 直接给出"按用户解析"的结果，跳过 DB / Redis */
    private static final class StubConfigService extends AiConfigService {
        private final AiProperties props;
        private AiClientConfig user;
        private String globalKey;

        StubConfigService(AiProperties props) {
            super(null, null, props, null);
            this.props = props;
            this.globalKey = props.getApiKey();
        }

        @Override
        public AiClientConfig resolve(Long userId) {
            if (user != null && user.hasKey()) {
                return user;
            }
            return new AiClientConfig("服务端默认", props.getUrl(), globalKey, props.getModel());
        }
    }
}
