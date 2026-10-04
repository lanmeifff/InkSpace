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

    /** 允许跨域的来源（不在白名单的 Origin 会被 Spring 以 403 拒绝） */
    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8088");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
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
