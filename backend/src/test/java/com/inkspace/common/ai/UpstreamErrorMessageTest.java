package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AiProperties;
import com.inkspace.service.AiConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游报错时必须把它的原话带出来 —— 否则用户只看到"AI 服务调用失败"，
 * 根本不知道是模型名写错、Key 无效还是余额不足（这正是本地踩过的坑）。
 *
 * 用 JDK 自带的 HttpServer 起一个伪上游，不依赖外网。
 */
class UpstreamErrorMessageTest {

    private static HttpServer server;
    private static String badModelBody;
    private static String plainTextBody;
    private static String baseUrl;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
        badModelBody = "{\"error\":{\"message\":\"The supported API model names are deepseek-flash, "
                + "deepseek-v4-pro, but you passed gpt7.0 astra.\",\"type\":\"invalid_request_error\"}}";
        plainTextBody = "Authentication Fails (governor)";

        server.createContext("/v1/chat/completions", exchange -> {
            // 路径里带 /plain 的返回非 JSON，用来验证回退逻辑
            boolean plain = exchange.getRequestURI().getPath().contains("plain");
            byte[] bytes = (plain ? plainTextBody : badModelBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @Test
    void upstreamMessageIsSurfacedToTheUser() {
        BizException e = assertThrows(BizException.class, () -> client(baseUrl).chat(1L, messages(), false));
        assertEquals(ErrorCode.AI_UPSTREAM_ERROR, e.getErrorCode());
        assertTrue(e.getMessage().contains("supported API model names"),
                "应当带上上游原话，实际：" + e.getMessage());
        assertTrue(e.getMessage().contains("gpt7.0 astra"), "应当指出用户传的模型名");
    }

    @Test
    void nonJsonBodyFallsBackToRawText() {
        String url = baseUrl + "/plain";
        BizException e = assertThrows(BizException.class, () -> client(url).chat(1L, messages(), false));
        assertEquals(ErrorCode.AI_UPSTREAM_ERROR, e.getErrorCode());
        assertTrue(e.getMessage().contains("Authentication Fails"), "非 JSON 错误体也要带回来");
    }

    private static List<LlmMessage> messages() {
        return List.of(LlmMessage.user("ping"));
    }

    private static OpenAiCompatibleLlmClient client(String url) {
        ObjectMapper mapper = new ObjectMapper();
        AiProperties properties = new AiProperties();
        properties.setUrl(url);
        properties.setApiKey("sk-test");
        properties.setModel("gpt7.0 astra");
        return new OpenAiCompatibleLlmClient(
                new FixedConfigService(properties, url), new MockLlmClient(), mapper, properties);
    }

    /** 固定返回测试用的接入参数，不查库也不查缓存 */
    private static final class FixedConfigService extends AiConfigService {
        private final AiClientConfig config;

        FixedConfigService(AiProperties properties, String url) {
            super(null, null, properties, null);
            this.config = new AiClientConfig("测试", url, properties.getApiKey(), properties.getModel(), false);
        }

        @Override
        public AiClientConfig resolve(Long userId) {
            return config;
        }
    }
}
