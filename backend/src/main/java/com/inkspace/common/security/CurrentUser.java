package com.inkspace.common.security;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 取当前登录用户的工具类。未登录时抛 401，避免在业务里到处判空。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static Long id() {
        return get().getId();
    }
}
