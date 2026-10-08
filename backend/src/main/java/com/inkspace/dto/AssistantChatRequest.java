package com.inkspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 通用助手对话请求：由前端回传完整历史（含本轮提问），后端不再保存会话状态。
 *
 * 之所以让前端带历史而不是后端存会话：
 * - 助手是无状态接口，刷新页面或换设备不丢上下文由前端自己决定；
 * - 服务端不必为对话引入新的存储与清理逻辑。
 * 代价是每轮都要重传历史，因此这里把条数和长度都卡死。
 */
public class AssistantChatRequest {

    /** 含本轮提问，最后一条必须是 user */
    @NotEmpty(message = "对话内容不能为空")
    @Size(max = 20, message = "对话历史最多 20 条")
    @Valid
    private List<ChatMessage> messages;

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
