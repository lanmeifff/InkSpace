package com.inkspace.service;

import com.inkspace.common.ratelimit.LoginRateLimiter;
import com.inkspace.common.security.AuthUser;
import com.inkspace.domain.entity.User;
import com.inkspace.dto.LoginRequest;
import com.inkspace.vo.LoginResponse;
import com.inkspace.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证编排：限流 → 校验账号密码 → 签发 Access + Refresh。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(UserService userService,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter loginRateLimiter) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        loginRateLimiter.check(clientIp);
        User user = userService.authenticate(request);
        log.info("用户登录成功 userId={}, ip={}", user.getId(), clientIp);
        return issueTokens(user);
    }

    /** 用 Refresh Token 换新的一对令牌（轮换：旧的立即失效） */
    public LoginResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userService.requireActiveUser(rotation.userId());
        log.info("刷新令牌成功 userId={}", user.getId());
        return LoginResponse.of(
                jwtService.createAccessToken(toAuthUser(user)),
                rotation.refreshToken(),
                jwtService.getAccessTtlSeconds(),
                UserVO.from(user));
    }

    /** 登出：吊销该设备会话（幂等） */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(toAuthUser(user));
        String refreshToken = refreshTokenService.issue(user.getId());
        return LoginResponse.of(accessToken, refreshToken, jwtService.getAccessTtlSeconds(), UserVO.from(user));
    }

    private AuthUser toAuthUser(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }
}
