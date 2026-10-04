package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.domain.entity.User;
import com.inkspace.dto.LoginRequest;
import com.inkspace.dto.PasswordChangeRequest;
import com.inkspace.dto.ProfileUpdateRequest;
import com.inkspace.dto.RegisterRequest;
import com.inkspace.mapper.UserMapper;
import com.inkspace.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 用户注册、登录与资料维护。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int STATUS_NORMAL = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserVO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(username);
        user.setAvatarUrl("");
        user.setRole("USER");
        user.setStatus(STATUS_NORMAL);
        userMapper.insert(user);

        // created_at/updated_at 由数据库默认值生成，INSERT 不会回填实体，重新查一次保证返回完整
        User saved = userMapper.selectById(user.getId());
        log.info("用户注册成功 username={}, id={}", username, saved.getId());
        return UserVO.from(saved);
    }

    /**
     * 校验账号密码，返回用户实体（令牌签发由 AuthService 负责）。
     */
    public User authenticate(LoginRequest request) {
        String account = request.getAccount().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account.toLowerCase(Locale.ROOT)));

        // 用户不存在与密码错误返回同一提示：避免账号枚举
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        log.info("用户登录成功 username={}, id={}", user.getUsername(), user.getId());
        return user;
    }

    /** 按 id 取用户并校验状态，供认证/资料接口复用 */
    public User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return user;
    }

    /** 当前用户资料视图 */
    public UserVO getProfile(Long userId) {
        return UserVO.from(requireActiveUser(userId));
    }

    /** 修改昵称/头像 */
    public UserVO updateProfile(Long userId, ProfileUpdateRequest request) {
        requireActiveUser(userId);
        User update = new User();
        update.setNickname(request.getNickname().trim());
        update.setAvatarUrl(request.getAvatarUrl() == null ? "" : request.getAvatarUrl());
        userMapper.update(update, new LambdaQueryWrapper<User>().eq(User::getId, userId));
        return getProfile(userId);
    }

    /** 修改密码：先验旧密码，成功后由调用方吊销全部会话 */
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = requireActiveUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "当前密码不正确");
        }
        User update = new User();
        update.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.update(update, new LambdaQueryWrapper<User>().eq(User::getId, userId));
        log.info("用户修改密码 userId={}", userId);
    }
}
