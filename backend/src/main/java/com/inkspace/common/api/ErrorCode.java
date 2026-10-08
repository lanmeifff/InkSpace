package com.inkspace.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码：code 用于前端逻辑判断，httpStatus 用于 HTTP 语义。
 * 约定：4xx 客户端错误、5xx 服务端错误、其余业务失败用 200 + 业务 code。
 */
public enum ErrorCode {

    SUCCESS(200, "成功", HttpStatus.OK),
    PARAM_ERROR(400, "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "无权访问", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),

    // 用户模块
    USERNAME_EXISTS(40001, "用户名已被占用", HttpStatus.BAD_REQUEST),
    EMAIL_EXISTS(40002, "邮箱已被注册", HttpStatus.BAD_REQUEST),
    UPLOAD_TYPE_INVALID(40003, "仅支持 jpg/png/gif/webp 图片", HttpStatus.BAD_REQUEST),
    UPLOAD_TOO_LARGE(40004, "文件超过大小限制（图片最大 5MB）", HttpStatus.BAD_REQUEST),
    CLIP_FAILED(40005, "网页抓取失败，请检查链接是否可访问", HttpStatus.BAD_REQUEST),
    // 用户不存在与密码错误返回同一提示，避免账号枚举
    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40102, "登录状态无效，请重新登录", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(40103, "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(40301, "账号已被禁用", HttpStatus.FORBIDDEN),
    TOO_MANY_REQUESTS(42901, "操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    // 并发编辑冲突：带上最新版本号重试
    VERSION_CONFLICT(40901, "内容已被其他修改更新，请刷新后重试", HttpStatus.CONFLICT),
    // AI 相关
    AI_NOT_CONFIGURED(50001, "AI 服务未配置（缺少 API Key）", HttpStatus.SERVICE_UNAVAILABLE),
    AI_CALL_FAILED(50002, "AI 服务调用失败，请稍后重试", HttpStatus.BAD_GATEWAY),
    // 上游明确告诉我们哪里配错了（模型名不存在、Key 无效、余额不足…），
    // 这类错误把上游原话带回给用户，否则用户只能对着"调用失败"猜
    AI_UPSTREAM_ERROR(50003, "AI 服务返回错误：请检查接口地址与模型名", HttpStatus.BAD_REQUEST),
    AI_QUOTA_EXCEEDED(42902, "今日 AI 调用次数已用完", HttpStatus.TOO_MANY_REQUESTS);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
