package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * URL 剪藏请求。
 */
public class ClipRequest {

    @NotBlank(message = "url 不能为空")
    @Size(max = 512, message = "url 过长")
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
