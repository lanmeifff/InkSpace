package com.inkspace.controller;

import com.inkspace.common.api.Result;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.LoginRequest;
import com.inkspace.dto.PasswordChangeRequest;
import com.inkspace.dto.ProfileUpdateRequest;
import com.inkspace.dto.RefreshTokenRequest;
import com.inkspace.dto.RegisterRequest;
import com.inkspace.service.AuthService;
import com.inkspace.service.RefreshTokenService;
import com.inkspace.service.UserService;
import com.inkspace.vo.LoginResponse;
import com.inkspace.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出 / 当前用户。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, AuthService authService,
                          RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @PostMapping("/login")
    @OperationLog(action = "USER_LOGIN", resourceType = "USER")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest servletRequest) {
        return Result.ok(authService.login(request, clientIp(servletRequest)));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    /** 登出不要求 Access Token 有效（Access 过期也应能登出），用 Refresh Token 吊销会话 */
    @PostMapping("/logout")
    @OperationLog(action = "USER_LOGOUT", resourceType = "USER")
    public Result<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getProfile(CurrentUser.id()));
    }

    @PutMapping("/profile")
    @OperationLog(action = "USER_PROFILE_UPDATE", resourceType = "USER")
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(userService.updateProfile(CurrentUser.id(), request));
    }

    /** 改密成功后吊销全部会话：其他设备必须重新登录 */
    @PutMapping("/password")
    @OperationLog(action = "USER_PASSWORD_CHANGE", resourceType = "USER")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        Long userId = CurrentUser.id();
        userService.changePassword(userId, request);
        refreshTokenService.revokeAll(userId);
        return Result.ok();
    }

    /** 反向代理场景优先取 X-Forwarded-For 的第一段 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
