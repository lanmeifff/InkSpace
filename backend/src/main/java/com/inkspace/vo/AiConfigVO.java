package com.inkspace.vo;

/**
 * 用户 AI 配置回显。apiKey 只回显掩码（sk-t****abcd），明文不出后端。
 *
 * @param source    当前生效来源：user（用你自己的 Key）/ global（用服务端配置的 Key）/ none（都没配）
 * @param model     你自己填的模型名（空表示没配）
 * @param provider  当前生效的服务商名称
 * @param effectiveModel 当前生效的模型名（可能来自服务端默认）
 * @param mock      true 表示当前走的是假数据（服务端开了 mock 且你没配 Key），界面要提示用户
 */
public record AiConfigVO(
        String providerName,
        String url,
        String apiKeyMask,
        boolean hasApiKey,
        String model,
        String source,
        String effectiveProvider,
        String effectiveModel,
        boolean mock) {
}
