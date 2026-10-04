package com.inkspace.common.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 能力抽象：屏蔽具体厂商（当前为 OpenAI 兼容协议的 HTTP 实现）。
 * 换模型/换厂商只需要换实现类，业务代码不感知。
 */
public interface LlmClient {

    /** 非流式调用；jsonMode=true 时要求模型返回 JSON 对象 */
    LlmResult chat(List<LlmMessage> messages, boolean jsonMode);

    /** 流式调用：每收到一段增量文本回调一次 onDelta */
    void chatStream(List<LlmMessage> messages, Consumer<String> onDelta);
}
