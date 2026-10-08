package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 「测试连接」用的最小调用：发一句 "ping"，只要能拿回内容就算通。
 * 用独立的短超时 HttpClient，避免把请求线程挂满 60 秒。
 */
@Component
public class LlmPing {

    private static final Logger log = LoggerFactory.getLogger(LlmPing.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public LlmPing(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return 模型回的第一句话（截断到 200 字）；失败抛 AI_CALL_FAILED */
    public String ping(AiClientConfig config) {
        if (!config.hasKey()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("temperature", 0);
        body.put("max_tokens", 32);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", "ping");
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 测试连接是最该说清楚的地方：直接把上游原话回给用户
                String detail = UpstreamError.describe(objectMapper, response.statusCode(), response.body());
                log.warn("AI 测试连接失败 status={} url={} detail={}", response.statusCode(), config.url(), detail);
                throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, detail);
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return content.isEmpty() ? "连接正常（模型未返回文本）" : truncate(content);
        } catch (IOException e) {
            log.warn("AI 测试连接网络异常 url={}", config.url(), e);
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
