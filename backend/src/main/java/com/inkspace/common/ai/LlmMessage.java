package com.inkspace.common.ai;

/**
 * 对话消息（role = system / user / assistant）。
 */
public record LlmMessage(String role, String content) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }
}
