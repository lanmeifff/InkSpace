package com.inkspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 接入配置（OpenAI 兼容协议）。
 * apiKey 为空时 AI 接口返回明确的"未配置"提示，不影响其他功能。
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 完整接口地址，如 https://api.deepseek.com/v1/chat/completions */
    private String url = "https://api.deepseek.com/v1/chat/completions";

    private String apiKey = "";

    private String model = "deepseek-chat";

    private int timeoutSeconds = 60;

    /** 每用户每日调用次数上限 */
    private int dailyQuota = 50;

    /** true 时不调用真实模型，返回固定假数据 */
    private boolean mock = false;

    /** 费用估算单价（元 / 1K tokens），仅用于 ai_task 成本统计 */
    private double pricePer1kTokens = 0.002;

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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(int dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public double getPricePer1kTokens() {
        return pricePer1kTokens;
    }

    public void setPricePer1kTokens(double pricePer1kTokens) {
        this.pricePer1kTokens = pricePer1kTokens;
    }
}
