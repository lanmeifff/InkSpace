package com.inkspace.vo;

/**
 * 登录 / 刷新成功后的响应：双令牌 + 用户信息。
 */
public class LoginResponse {

    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    /** Access Token 有效期（秒），前端据此做无感刷新 */
    private long expiresIn;
    private UserVO user;

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UserVO user) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(expiresIn);
        response.setUser(user);
        return response;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }
}
