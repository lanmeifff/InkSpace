package com.inkspace.common.security;

/**
 * 认证后的当前用户（SecurityContext 中的 principal）。
 * 只放令牌里已有的最小信息，需要完整资料时再查库。
 */
public class AuthUser {

    private final Long id;
    private final String username;
    private final String role;

    public AuthUser(Long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
