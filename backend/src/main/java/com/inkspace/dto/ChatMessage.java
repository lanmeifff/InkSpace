package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 多轮对话里的一条消息。role 只允许 user / assistant，由前端回传历史。
 */
public class ChatMessage {

    @NotBlank(message = "消息角色不能为空")
    @Size(max = 16, message = "消息角色过长")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "单条消息不能超过 4000 字")
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
