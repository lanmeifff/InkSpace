package com.inkspace.common.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 能力抽象：屏蔽具体厂商（当前为 OpenAI 兼容协议的 HTTP 实现）。
 * 换模型/换厂商只需要换实现类，业务代码不感知。
 *
 * 调用必须带上 userId —— 接入参数（Key / 模型 / 接口地址）是按用户解析的。
 */
public interface LlmClient {

    /** 非流式调用；jsonMode=true 时要求模型返回 JSON 对象 */
    LlmResult chat(Long userId, List<LlmMessage> messages, boolean jsonMode);

    /** 流式调用：每收到一段增量文本回调一次 onDelta */
    void chatStream(Long userId, List<LlmMessage> messages, Consumer<String> onDelta);

    /** 该用户当前生效的模型名，用于 ai_task 审计 */
    String currentModel(Long userId);
}
