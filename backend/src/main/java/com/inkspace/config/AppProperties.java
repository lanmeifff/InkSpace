package com.inkspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用级配置（分享前缀、上传目录、跨域白名单）。
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 分享链接前缀，前端拼 token 用 */
    private String shareBaseUrl = "http://localhost:3000/share/";

    /** 上传目录 */
    private String uploadDir = "./uploads";

    /** 上传大小上限（字节），默认 5MB */
    private long uploadMaxBytes = 5 * 1024 * 1024;

    /**
     * 允许跨域的来源（不在白名单的 Origin 会被 Spring 以 403 拒绝）。
     * 注意 localhost 与 127.0.0.1 在浏览器看来是两个不同来源，开发时两种写法都要列上，
     * 否则预检被拒、前端只会报 "Failed to fetch"。
     * 部署时用 ALLOWED_ORIGINS 环境变量覆盖成真实域名。
     */
    private List<String> allowedOrigins = List.of(
            "http://localhost:3000", "http://127.0.0.1:3000",
            "http://localhost:8088", "http://127.0.0.1:8088");

    /**
     * 允许跨域的来源模式，支持端口通配，例如 http://localhost:*。
     *
     * 本地开发经常换端口（3000 被占就起 3100、3200），逐个写进白名单很容易漏，
     * 而漏掉的后果只是前端报 "Failed to fetch"，排查成本高，所以本机来源一律放通。
     * 这些模式只覆盖 localhost / 127.0.0.1 / [::1]；线上域名仍必须走 allowedOrigins 显式声明。
     */
    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public long getUploadMaxBytes() {
        return uploadMaxBytes;
    }

    public void setUploadMaxBytes(long uploadMaxBytes) {
        this.uploadMaxBytes = uploadMaxBytes;
    }

    public String getShareBaseUrl() {
        return shareBaseUrl;
    }

    public void setShareBaseUrl(String shareBaseUrl) {
        this.shareBaseUrl = shareBaseUrl;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}
