package com.inkspace.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把上游返回的错误体整理成一句能给人看的话。
 *
 * 上游（OpenAI 兼容协议）在 4xx/5xx 时通常会返回结构化错误，例如：
 *   {"error":{"message":"The supported API model names are deepseek-flash, ...","type":"invalid_request_error"}}
 *   {"error":{"message":"Authentication Fails (governor)","type":"authentication_error"}}
 *
 * 这些正是用户改配置所需要的提示（模型名写错 / Key 无效 / 余额不足），
 * 直接丢成"调用失败"等于让用户猜，所以这里尽量把它带出来。
 */
public final class UpstreamError {

    private static final Logger log = LoggerFactory.getLogger(UpstreamError.class);
    private static final int MAX_LENGTH = 220;

    private UpstreamError() {
    }

    /**
     * @param status 上游 HTTP 状态码
     * @param body   上游响应体（可能不是 JSON）
     * @return 供用户阅读的错误摘要；解析不出时回退成截断后的原始响应
     */
    public static String describe(ObjectMapper objectMapper, int status, String body) {
        String message = extractMessage(objectMapper, body);
        if (message == null || message.isBlank()) {
            message = body == null ? "" : body;
        }
        String cleaned = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (cleaned.isEmpty()) {
            return "上游返回 HTTP " + status + "，且响应体为空";
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH) + "…";
        }
        return cleaned;
    }

    private static String extractMessage(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            // OpenAI 兼容：{"error":{"message":...}}；也兼容 {"message":...} / {"error":"..."}
            JsonNode error = root.path("error");
            if (error.isObject() && error.hasNonNull("message")) {
                return error.path("message").asText();
            }
            if (error.isTextual()) {
                return error.asText();
            }
            if (root.hasNonNull("message")) {
                return root.path("message").asText();
            }
        } catch (Exception e) {
            log.debug("上游错误体不是 JSON，按原文截断返回");
        }
        return null;
    }
}
