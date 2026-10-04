package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌 / 登出请求体。
 */
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
