package com.inkspace.common.ai;

/**
 * 一次非流式调用的结果（含 token 用量，供审计与成本统计）。
 */
public record LlmResult(String content, int promptTokens, int completionTokens) {
}
