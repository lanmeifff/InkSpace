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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * OpenAI 兼容协议的 LLM 客户端（DeepSeek / 通义 / OpenAI 均可）。
 * 只用 JDK 自带 HttpClient，不引入额外 SDK —— 协议很简单：POST JSON + SSE 流。
 *
 * 每次调用按传入的 userId 解析接入参数：用户自己配了就用自己的 Key/模型，
 * 没配则回落到 app.ai.* 的服务端默认。配置解析显式传参，不依赖 ThreadLocal ——
 * SSE 问答跑在 aiExecutor 线程上，那里拿不到请求线程的 SecurityContext。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock", havingValue = "false", matchIfMissing = true)
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";
    private static final int TIMEOUT_FALLBACK_SECONDS = 60;

    private final AiConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    public OpenAiCompatibleLlmClient(AiConfigService configService,
                                     ObjectMapper objectMapper,
                                     AiProperties properties) {
        this.configService = configService;
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
        HttpResponse<String> response = send(config, buildBody(config, messages, jsonMode, false));
        if (response.statusCode() != 200) {
            log.warn("LLM 调用失败 status={} body={}", response.statusCode(), truncate(response.body()));
            throw new BizException(ErrorCode.AI_CALL_FAILED);
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
                throw new BizException(ErrorCode.AI_CALL_FAILED);
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
        return configService.resolve(userId).model();
    }

    private AiClientConfig resolve(Long userId) {
        AiClientConfig config = configService.resolve(userId);
        if (!config.hasKey()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
        if (config.url() == null || config.url().isBlank()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
        return config;
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
