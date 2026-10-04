package com.inkspace.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置。secret 为空或不足 32 字节时启动即失败（fail fast），避免线上带着弱密钥跑。
 */
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank(message = "jwt.secret 未配置：请在 application-local.yml 或环境变量 JWT_SECRET 中提供（>=32 字节）")
    private String secret;

    @Min(1)
    private long accessTtlMinutes = 30;

    @Min(1)
    private long refreshTtlDays = 14;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }
}
