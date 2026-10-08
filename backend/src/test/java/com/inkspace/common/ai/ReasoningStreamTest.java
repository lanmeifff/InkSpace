package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推理型模型的流式解析：delta 里同时有 reasoning_content 与 content。
 *
 * 正文只能进回答，思维链只能进"思考中"回调 —— 混在一起会把模型的自言自语
 * 当成答案显示给用户；而完全丢掉思维链，模型全力思考时界面会一直空白。
 */
class ReasoningStreamTest {

    private static HttpServer server;
    private static String url;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
        server.createContext("/v1/chat/completions", exchange -> {
            // 模拟 deepseek-flash：先纯思维链，再思维链+正文，最后收尾
            String sse = String.join("\n\n",
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"用户想要一个水果名\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"但没给上下文\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"不确定你指的是\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"哪个项目。\"}}]}",
                    "data: [DONE]") + "\n\n";
            byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
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
    void contentAndReasoningGoToTheirOwnCallbacks() {
        ObjectMapper mapper = new ObjectMapper();
        AiProperties properties = new AiProperties();
        properties.setUrl(url);
        properties.setApiKey("sk-test");
        properties.setModel("reasoning-model");
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                new FixedConfigService(properties, url), new MockLlmClient(), mapper, properties);

        StringBuilder answer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        client.chatStream(1L, List.of(LlmMessage.user("这个项目用什么水果当代号？")),
                answer::append, thinking::append);

        assertEquals("不确定你指的是哪个项目。", answer.toString(), "正文只应包含 content 部分");
        assertTrue(thinking.toString().contains("水果名"), "思维链应当单独回调，实际：" + thinking);
        assertTrue(answer.toString().indexOf("用户想要") < 0, "思维链不能混进回答");
    }

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
