package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改资料（昵称 / 头像地址）。
 */
public class ProfileUpdateRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不能超过 32 字")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
