package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock", havingValue = "false", matchIfMissing = true)
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public LlmResult chat(List<LlmMessage> messages, boolean jsonMode) {
        requireApiKey();
        HttpResponse<String> response = send(buildBody(messages, jsonMode, false));
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
    public void chatStream(List<LlmMessage> messages, Consumer<String> onDelta) {
        requireApiKey();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages, false, true).toString()))
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

    private HttpResponse<String> send(ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
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

    private ObjectNode buildBody(List<LlmMessage> messages, boolean jsonMode, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
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

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
