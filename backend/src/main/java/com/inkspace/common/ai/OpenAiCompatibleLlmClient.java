package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AiProperties;
import com.inkspace.service.AiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * LLM 客户端：按"每次调用传入的 userId"解析接入参数。
 *
 * 用户自己配了就用自己的 Key/模型；没配则回落到 app.ai.* 的服务端默认。
 * 服务端开了 mock 且用户没配 Key 时，这一路请求转交 MockLlmClient，
 * 返回假数据（界面上会提示处于 mock 模式）。
 *
 * 配置解析显式传参，不依赖 ThreadLocal —— SSE 问答跑在 aiExecutor 线程上，
 * 那里拿不到请求线程的 SecurityContext。
 *
 * 标 &#64;Primary：LlmClient 现在有两个实现（本类与 MockLlmClient），
 * 业务侧只该注入这一个；mock 由本类在内部按配置转发。
 */
@Component
@Primary
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";
    private static final int TIMEOUT_FALLBACK_SECONDS = 60;

    private final AiConfigService configService;
    private final MockLlmClient mockClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    public OpenAiCompatibleLlmClient(AiConfigService configService,
                                     MockLlmClient mockClient,
                                     ObjectMapper objectMapper,
                                     AiProperties properties) {
        this.configService = configService;
        this.mockClient = mockClient;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : TIMEOUT_FALLBACK_SECONDS;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public LlmResult chat(Long userId, List<LlmMessage> messages, boolean jsonMode) {
        AiClientConfig config = resolve(userId);
        if (config.mock()) {
            return mockClient.chat(userId, messages, jsonMode);
        }
        requireUsable(config);
        HttpResponse<String> response = send(config, buildBody(config, messages, jsonMode, false));
        if (response.statusCode() != 200) {
            throw upstreamFailure(config, response.statusCode(), response.body());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new LlmResult(content, promptTokens, completionTokens);
        } catch (IOException e) {
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    @Override
    public void chatStream(Long userId, List<LlmMessage> messages, Consumer<String> onDelta) {
        AiClientConfig config = resolve(userId);
        if (config.mock()) {
            mockClient.chatStream(userId, messages, onDelta);
            return;
        }
        requireUsable(config);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(config, messages, false, true).toString()))
                .build();
        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                // 流式响应的错误体也在这里，读一行就能拿到上游的说明
                String detail = response.body().limit(3).collect(java.util.stream.Collectors.joining(" "));
                throw upstreamFailure(config, response.statusCode(), detail);
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> parseSseLine(line, onDelta));
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    @Override
    public String currentModel(Long userId) {
        AiClientConfig config = configService.resolve(userId);
        // mock 模式下把真实模型名标成 mock，避免 ai_task 审计里出现"看起来调了真模型"的记录
        return config.mock() ? "mock" : config.model();
    }

    /**
     * 上游返回非 200：把它的原话带给用户，而不是只丢一句"调用失败"。
     * 模型名写错、Key 无效、余额不足这些都能从上游响应里读出来。
     */
    private BizException upstreamFailure(AiClientConfig config, int status, String body) {
        String detail = UpstreamError.describe(objectMapper, status, body);
        log.warn("LLM 调用失败 status={} url={} model={} detail={}",
                status, config.url(), config.model(), detail);
        return new BizException(ErrorCode.AI_UPSTREAM_ERROR, detail);
    }

    /** 真调上游前才校验配置；mock 分支不需要 Key，所以校验不放在更早的位置 */
    private void requireUsable(AiClientConfig config) {
        if (!config.hasKey() || config.url() == null || config.url().isBlank()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
    }

    private AiClientConfig resolve(Long userId) {
        return configService.resolve(userId);
    }

    private void parseSseLine(String line, Consumer<String> onDelta) {
        if (line == null || !line.startsWith(DATA_PREFIX)) {
            return;
        }
        String payload = line.substring(DATA_PREFIX.length()).trim();
        if (payload.isEmpty() || DONE.equals(payload)) {
            return;
        }
        try {
            JsonNode delta = objectMapper.readTree(payload)
                    .path("choices").path(0).path("delta").path("content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
                onDelta.accept(delta.asText());
            }
        } catch (IOException ignored) {
            // SSE 心跳与不完整分片直接跳过
        }
    }

    private HttpResponse<String> send(AiClientConfig config, ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("LLM 网络异常", e);
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    private ObjectNode buildBody(AiClientConfig config, List<LlmMessage> messages, boolean jsonMode, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("temperature", 0.3);
        if (stream) {
            body.put("stream", true);
        }
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode array = body.putArray("messages");
        for (LlmMessage message : messages) {
            ObjectNode node = array.addObject();
            node.put("role", message.role());
            node.put("content", message.content());
        }
        return body;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
