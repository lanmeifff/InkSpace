package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 保存用户自带的 AI 接入配置。
 * apiKey 留空表示"沿用已保存的 Key"（前端不回显明文，用户不重填就不该被清掉）。
 */
public class AiConfigRequest {

    @Size(max = 32, message = "名称不能超过 32 字")
    private String providerName;

    /** 完整接口地址，例如 https://api.deepseek.com/v1/chat/completions */
    @NotBlank(message = "接口地址不能为空")
    @Size(max = 255, message = "接口地址过长")
    @Pattern(regexp = "^https?://.+", message = "接口地址需以 http:// 或 https:// 开头")
    private String url;

    @Size(max = 200, message = "API Key 过长")
    private String apiKey;

    @NotBlank(message = "模型名不能为空")
    @Size(max = 64, message = "模型名不能超过 64 字")
    private String model;

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
