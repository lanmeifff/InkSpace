package com.inkspace.common.ai;

/**
 * 一次调用实际要用的接入参数（模型 + 接口地址 + Key）。
 * 来源可能是用户自带的配置，也可能是服务端 application.yml 的默认配置。
 *
 * @param mock true 表示这次调用走假数据，不真的请求上游。
 *             由 app.ai.mock 与服务端是否配了 Key 共同决定；
 *             用户自己配了 Key 时永远为 false —— 否则用户填的配置会被静默忽略。
 */
public record AiClientConfig(String providerName, String url, String apiKey, String model, boolean mock) {

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
