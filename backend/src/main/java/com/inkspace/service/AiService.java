package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.common.ai.LlmClient;
import com.inkspace.common.ai.LlmMessage;
import com.inkspace.common.ai.LlmResult;
import com.inkspace.common.ai.PromptLibrary;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AiProperties;
import com.inkspace.domain.entity.AiTask;
import com.inkspace.domain.entity.Note;
import com.inkspace.mapper.AiTaskMapper;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.vo.CitationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 能力：摘要（含长文 map-reduce）、自动标签、基于笔记的问答、周报。
 * 每次调用落 ai_task 审计，摘要按笔记版本缓存，按用户限每日配额。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String SUMMARY_KEY = "ai:summary:";
    private static final String QUOTA_KEY = "ai:quota:";
    private static final Duration SUMMARY_TTL = Duration.ofDays(7);
    private static final int SINGLE_CALL_LIMIT = 3000;
    private static final int CHUNK_SIZE = 2000;
    private static final int RETRIEVE_TOP_K = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LlmClient llmClient;
    private final PromptLibrary prompts;
    private final NoteService noteService;
    private final NoteMapper noteMapper;
    private final AiTaskMapper aiTaskMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public AiService(LlmClient llmClient,
                     PromptLibrary prompts,
                     NoteService noteService,
                     NoteMapper noteMapper,
                     AiTaskMapper aiTaskMapper,
                     StringRedisTemplate redis,
                     ObjectMapper objectMapper,
                     AiProperties properties) {
        this.llmClient = llmClient;
        this.prompts = prompts;
        this.noteService = noteService;
        this.noteMapper = noteMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // 摘要

    public String summarize(Long userId, Long noteId) {
        Note note = noteService.requireOwned(userId, noteId);
        String cacheKey = SUMMARY_KEY + noteId + ":" + note.getVersion();
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            record(userId, "summary", "success", noteId, "cache", 0, 0, 0, "");
            return cached;
        }

        checkQuota(userId);
        long start = System.currentTimeMillis();
        String summary;
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            String content = note.getContent() == null ? "" : note.getContent();
            if (content.length() <= SINGLE_CALL_LIMIT) {
                LlmResult result = llmClient.chat(userId, List.of(
                        LlmMessage.system("你是严谨的笔记摘要助手。"),
                        LlmMessage.user(prompts.render("summarize-chunk",
                                Map.of("title", note.getTitle(), "content", content)))), false);
                summary = result.content();
                promptTokens = result.promptTokens();
                completionTokens = result.completionTokens();
            } else {
                // 长文 map-reduce：先分块摘要，再归并
                List<String> chunks = splitChunks(content, CHUNK_SIZE);
                List<String> partial = new ArrayList<>();
                for (String chunk : chunks) {
                    LlmResult result = llmClient.chat(userId, List.of(
                            LlmMessage.system("你是严谨的笔记摘要助手。"),
                            LlmMessage.user(prompts.render("summarize-chunk",
                                    Map.of("title", note.getTitle(), "content", chunk)))), false);
                    partial.add(result.content());
                    promptTokens += result.promptTokens();
                    completionTokens += result.completionTokens();
                }
                LlmResult merged = llmClient.chat(userId, List.of(
                        LlmMessage.system("你是严谨的笔记摘要助手。"),
                        LlmMessage.user(prompts.render("summarize-merge",
                                Map.of("title", note.getTitle(), "chunks", String.join("\n---\n", partial))))), false);
                summary = merged.content();
                promptTokens += merged.promptTokens();
                completionTokens += merged.completionTokens();
            }
            redis.opsForValue().set(cacheKey, summary, SUMMARY_TTL);
            record(userId, "summary", "success", noteId, llmClient.currentModel(userId),
                    promptTokens, completionTokens, System.currentTimeMillis() - start, "");
            return summary;
        } catch (BizException e) {
            record(userId, "summary", "failed", noteId, llmClient.currentModel(userId),
                    0, 0, System.currentTimeMillis() - start, String.valueOf(e.getErrorCode().getCode()));
            throw e;
        }
    }

    // 自动标签

    public List<String> autoTags(Long userId, Long noteId, boolean apply) {
        Note note = noteService.requireOwned(userId, noteId);
        checkQuota(userId);
        long start = System.currentTimeMillis();
        try {
            LlmResult result = llmClient.chat(userId, List.of(
                    LlmMessage.system("你是笔记标签助手，只输出 JSON。"),
                    LlmMessage.user(prompts.render("auto-tags",
                            Map.of("title", note.getTitle(),
                                    "content", truncate(note.getContent(), 3000))))), true);
            List<String> tags = parseTags(result.content());
            record(userId, "tag", "success", noteId, llmClient.currentModel(userId),
                    result.promptTokens(), result.completionTokens(),
                    System.currentTimeMillis() - start, "");
            if (apply && !tags.isEmpty()) {
                noteService.setTags(userId, noteId, tags);
            }
            return tags;
        } catch (BizException e) {
            record(userId, "tag", "failed", noteId, llmClient.currentModel(userId),
                    0, 0, System.currentTimeMillis() - start, String.valueOf(e.getErrorCode().getCode()));
            throw e;
        }
    }

    // 笔记问答（流式）

    public void ask(Long userId, String question,
                    Consumer<List<CitationVO>> onCitations,
                    Consumer<String> onDelta) {
        checkQuota(userId);
        long start = System.currentTimeMillis();

        List<Note> hits = noteMapper.search(new Page<>(1, RETRIEVE_TOP_K), userId, question.trim(),
                null, null, null, null).getRecords();
        List<CitationVO> citations = hits.stream()
                .map(note -> new CitationVO(note.getId(), note.getTitle()))
                .toList();
        onCitations.accept(citations);

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Note note = hits.get(i);
            context.append("[").append(i + 1).append("] 《").append(note.getTitle()).append("》：")
                    .append(truncate(note.getContentPlain(), 500)).append("\n");
        }

        String userPrompt = prompts.render("qa", Map.of(
                "context", context.isEmpty() ? "（没有检索到相关笔记）" : context.toString(),
                "question", question));
        try {
            llmClient.chatStream(userId, List.of(
                    LlmMessage.system("你是我的知识助理，只依据给定笔记片段回答。"),
                    LlmMessage.user(userPrompt)), onDelta);
            record(userId, "qa", "success", null, llmClient.currentModel(userId),
                    userPrompt.length() / 2, 0,
                    System.currentTimeMillis() - start, "");
        } catch (BizException e) {
            record(userId, "qa", "failed", null, llmClient.currentModel(userId),
                    0, 0, System.currentTimeMillis() - start, String.valueOf(e.getErrorCode().getCode()));
            throw e;
        }
    }

    // 通用助手对话（流式，不检索笔记）

    /** 助手人设：允许通用问答，同时知道用户在做笔记 */
    private static final String ASSISTANT_SYSTEM_PROMPT =
            "你是用户笔记库里的 AI 助手，直接和用户对话。"
                    + "可以正常回答通用问题、解释概念、写代码、翻译、闲聊，不要局限于笔记内容。"
                    + "如果你确实知道用户笔记里有相关内容，可以主动提一句。"
                    + "不确定的事情直说不确定，不要编造。用中文回答，除非用户用其他语言提问。";

    /**
     * 自由对话：不检索笔记，直接把多轮历史交给模型。
     * 与 ask() 的区别只有"不拼笔记上下文"，配额与审计逻辑一致。
     *
     * @param onThinking 推理型模型会先输出思维链；不接收的话模型全力思考时
     *                   调用方会长时间收不到任何回调，界面只能干等
     */
    public void assistant(Long userId, List<LlmMessage> messages,
                          Consumer<String> onDelta, Consumer<String> onThinking) {
        checkQuota(userId);
        long start = System.currentTimeMillis();
        int promptChars = messages.stream().mapToInt(message -> message.content().length()).sum();
        try {
            List<LlmMessage> payload = new ArrayList<>();
            payload.add(LlmMessage.system(ASSISTANT_SYSTEM_PROMPT));
            payload.addAll(messages);
            llmClient.chatStream(userId, payload, onDelta, onThinking);
            record(userId, "assistant", "success", null, llmClient.currentModel(userId),
                    promptChars / 2, 0, System.currentTimeMillis() - start, "");
        } catch (BizException e) {
            record(userId, "assistant", "failed", null, llmClient.currentModel(userId),
                    0, 0, System.currentTimeMillis() - start, String.valueOf(e.getErrorCode().getCode()));
            throw e;
        }
    }

    // 周报

    public String weekly(Long userId) {
        checkQuota(userId);
        long start = System.currentTimeMillis();

        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt)
                .ge(Note::getUpdatedAt, LocalDateTime.now().minusDays(7))
                .orderByDesc(Note::getUpdatedAt)
                .last("LIMIT 30"));
        StringBuilder notesText = new StringBuilder();
        for (Note note : notes) {
            notesText.append("- ").append(note.getTitle()).append("：")
                    .append(truncate(note.getContentPlain(), 120)).append("\n");
        }
        try {
            LlmResult result = llmClient.chat(userId, List.of(
                    LlmMessage.system("你是知识管理助手。"),
                    LlmMessage.user(prompts.render("weekly",
                            Map.of("notes", notesText.isEmpty() ? "（本周没有新增或修改的笔记）" : notesText.toString())))), false);
            record(userId, "weekly", "success", null, llmClient.currentModel(userId),
                    result.promptTokens(), result.completionTokens(),
                    System.currentTimeMillis() - start, "");
            return result.content();
        } catch (BizException e) {
            record(userId, "weekly", "failed", null, llmClient.currentModel(userId),
                    0, 0, System.currentTimeMillis() - start, String.valueOf(e.getErrorCode().getCode()));
            throw e;
        }
    }

    // 内部工具

    /** 每日配额：Redis INCR + 首次设置 24h 过期 */
    private void checkQuota(Long userId) {
        String key = QUOTA_KEY + userId + ":" + LocalDate.now().format(DAY);
        Long used = redis.opsForValue().increment(key);
        if (used != null && used == 1L) {
            redis.expire(key, Duration.ofHours(24));
        }
        if (used != null && used > properties.getDailyQuota()) {
            throw new BizException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
    }

    private void record(Long userId, String type, String status, Long sourceId, String model,
                        int promptTokens, int completionTokens, long latencyMs, String errorCode) {
        try {
            AiTask task = new AiTask();
            task.setUserId(userId);
            task.setTaskType(type);
            task.setStatus(status);
            task.setSourceId(sourceId);
            task.setModel(model);
            task.setPromptTokens(promptTokens);
            task.setCompletionTokens(completionTokens);
            task.setCost(BigDecimal.valueOf((promptTokens + completionTokens)
                            * properties.getPricePer1kTokens() / 1000.0)
                    .setScale(6, RoundingMode.HALF_UP));
            task.setLatencyMs((int) latencyMs);
            task.setErrorCode(errorCode == null ? "" : errorCode);
            aiTaskMapper.insert(task);
        } catch (Exception e) {
            log.warn("写 ai_task 失败 type={}", type, e);
        }
    }

    /** 解析模型返回的标签 JSON，容忍 ```json 代码块包裹 */
    private List<String> parseTags(String content) {
        try {
            String cleaned = content.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return List.of();
            }
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            List<String> tags = new ArrayList<>();
            node.path("tags").forEach(tag -> {
                String text = tag.asText("").trim();
                if (!text.isEmpty() && text.length() <= 32) {
                    tags.add(text);
                }
            });
            return tags.stream().distinct().limit(10).toList();
        } catch (Exception e) {
            log.warn("标签 JSON 解析失败：{}", truncate(content, 100));
            return List.of();
        }
    }

    /** 按段落贪心切块，避免把一句话劈开 */
    private List<String> splitChunks(String content, int maxLen) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\n\n")) {
            if (current.length() + paragraph.length() + 2 > maxLen && current.length() > 0) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (paragraph.length() > maxLen) {
                for (int i = 0; i < paragraph.length(); i += maxLen) {
                    chunks.add(paragraph.substring(i, Math.min(paragraph.length(), i + maxLen)));
                }
                continue;
            }
            current.append(paragraph).append("\n\n");
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
