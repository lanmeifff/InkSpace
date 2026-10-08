package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.inkspace.common.ai.AiClientConfig;
import com.inkspace.common.ai.LlmResult;
import com.inkspace.common.ai.LlmClient;
import com.inkspace.common.ai.LlmMessage;
import com.inkspace.domain.entity.Note;
import com.inkspace.domain.entity.UserAiConfig;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.mapper.UserAiConfigMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真调上游的端到端自检：证明不同文章得到不同结果、且用户配的 Key 真的生效。
 *
 * 这个测试会消耗真实额度（两次很小的调用），而且需要：
 *   · 本地 MySQL(3306) 与 Redis(6379)
 *   · 库里已经有一个用户配好了可用的 AI Key（url / model / key 都对）
 * 任一条件不满足就自动跳过，不会让 mvn test 变红。
 *
 * 之所以值得留着：用户报的"无论什么文章摘要都一模一样"就是因为
 * app.ai.mock 静默覆盖了用户配置，普通单测发现不了，只有真调一次才能证实。
 */
@SpringBootTest
class AiRealCallIntegrationTest {

    @Autowired
    private AiService aiService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private UserAiConfigMapper userAiConfigMapper;

    @Autowired
    private LlmClient llmClient;

    @Test
    void differentNotesGetDifferentAnswersFromTheRealModel() {
        Assumptions.assumeTrue(reachable(3306), "本地 MySQL 未启动，跳过热调真实模型");
        Assumptions.assumeTrue(reachable(6379), "本地 Redis 未启动，跳过热调真实模型");

        UserAiConfig stored = userAiConfigMapper.selectOne(
                new LambdaQueryWrapper<UserAiConfig>().orderByDesc(UserAiConfig::getUpdatedAt).last("LIMIT 1"));
        Assumptions.assumeTrue(stored != null, "库里没有用户配置过 AI，跳过热调真实模型");

        Long userId = stored.getUserId();
        AiClientConfig config = aiConfigService.resolve(userId);
        Assumptions.assumeTrue(config.hasKey() && !config.mock(),
                "没有可用的真实 Key（或仍处于 mock），跳过热调真实模型");

        // 先直接打一次上游，确认 Key/地址/模型三者确实能通（不通就跳过，不判失败）
        try {
            llmClient.chat(userId, List.of(LlmMessage.user("只回复两个字：收到")), false);
        } catch (Exception e) {
            Assumptions.abort("上游调用不通过（" + e.getMessage() + "），跳过热调真实模型");
        }

        // 造两个主题完全不同的笔记，分别摘要
        Note a = createNote(userId, "并发编程要点",
                "HashMap 在并发扩容时会形成环形链表，导致 get 操作死循环。"
                        + "解决办法是用 ConcurrentHashMap，它用 CAS + synchronized 保证桶级并发安全。");
        Note b = createNote(userId, "烘焙心得",
                "戚风蛋糕塌腰通常是因为出炉没有立刻倒扣，或者蛋白打发不到位。"
                        + "烤箱温度偏高也会让表面先结壳，内部继续膨胀时就撑不住了。");
        try {
            String summaryA = aiService.summarize(userId, a.getId());
            String summaryB = aiService.summarize(userId, b.getId());

            assertNotEquals(summaryA, summaryB, "两篇完全不同主题的文章，摘要不该一模一样（那就是 mock 的固定文案）");
            assertFalse(summaryA.contains("【mock 摘要】"), "不该再出现 mock 固定文案");
            assertFalse(summaryB.contains("【mock 摘要】"), "不该再出现 mock 固定文案");
            assertTrue(summaryA.length() > 10 && summaryB.length() > 10, "摘要不该是空响应");

            // 问答也验一次，避免只有摘要走对了路
            LlmResult answer = llmClient.chat(userId, List.of(
                    LlmMessage.system("只依据给定内容回答。"),
                    LlmMessage.user("戚风蛋糕塌腰的原因是什么？")), false);
            assertTrue(answer.content().length() > 0, "问答应当有内容");
            assertFalse(answer.content().contains("线程池"), "问烘焙不该答并发，说明 prompt 没被正确传递");
        } finally {
            // 清理：删掉两篇测试笔记
            noteMapper.delete(new LambdaQueryWrapper<Note>().in(Note::getId, List.of(a.getId(), b.getId())));
        }
    }

    private Note createNote(Long userId, String title, String plain) {
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(title);
        note.setContent(plain);
        note.setContentPlain(plain);
        note.setKind("manual");
        note.setStatus("normal");
        note.setIsFavorite(false);
        note.setIsPublic(false);
        note.setSourceUrl("");
        note.setVersion(1);
        noteMapper.insert(note);
        return note;
    }

    private static boolean reachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
