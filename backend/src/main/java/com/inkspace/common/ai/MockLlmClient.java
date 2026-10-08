package com.inkspace.common.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * mock 实现：不调用真实模型，按提示词特征返回固定假数据（app.ai.mock=true）。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", havingValue = "true", name = "mock")
public class MockLlmClient implements LlmClient {

    private static final String SUMMARY = "【mock 摘要】这段笔记主要讲了线程池的核心参数（核心线程数、最大线程数、"
            + "队列与拒绝策略）以及参数之间的配合关系，并给出了生产环境下的取值经验。";

    private static final String TAGS_JSON = "{\"tags\":[\"Java\",\"线程池\",\"并发\"]}";

    private static final String QA_ANSWER = "根据你的笔记：线程池的关键参数包括核心线程数、最大线程数、任务队列和拒绝策略 [1]；"
            + "生产环境通常建议使用有界队列并自定义拒绝策略，避免任务无限堆积 [1][2]。";

    private static final String WEEKLY = "## 本周概览\n- 主要围绕 Java 并发与 Redis 缓存两条线做笔记整理\n\n"
            + "## 值得沉淀\n1. 线程池四要素与拒绝策略的取舍\n2. 缓存穿透/击穿/雪崩的应对组合\n\n"
            + "## 下周方向\n- 补齐 MySQL 索引优化的实践记录";

    @Override
    public LlmResult chat(Long userId, List<LlmMessage> messages, boolean jsonMode) {
        String content = mockContent(messages);
        return new LlmResult(content, 128, 64);
    }

    @Override
    public void chatStream(Long userId, List<LlmMessage> messages, Consumer<String> onDelta) {
        String content = mockContent(messages);
        int chunkSize = 12;
        for (int i = 0; i < content.length(); i += chunkSize) {
            onDelta.accept(content.substring(i, Math.min(content.length(), i + chunkSize)));
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public String currentModel(Long userId) {
        return "mock";
    }

    private String mockContent(List<LlmMessage> messages) {
        String all = String.join("\n", messages.stream().map(LlmMessage::content).toList());
        if (all.contains("标签助手")) {
            return TAGS_JSON;
        }
        if (all.contains("知识助理")) {
            return QA_ANSWER;
        }
        if (all.contains("知识管理助手")) {
            return WEEKLY;
        }
        return SUMMARY;
    }
}
