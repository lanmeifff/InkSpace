package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：account 支持用户名或邮箱。
 */
public class LoginRequest {

    @NotBlank(message = "请输入用户名或邮箱")
    private String account;

    @NotBlank(message = "请输入密码")
    private String password;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
