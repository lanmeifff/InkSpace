package com.inkspace.common.ai;

/**
 * 一次调用实际要用的接入参数（模型 + 接口地址 + Key）。
 * 来源可能是用户自带的配置，也可能是服务端 application.yml 的默认配置。
 */
public record AiClientConfig(String providerName, String url, String apiKey, String model) {

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
