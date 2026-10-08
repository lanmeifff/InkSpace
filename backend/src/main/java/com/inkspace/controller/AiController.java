package com.inkspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.common.api.Result;
import com.inkspace.common.audit.AuditRecorder;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.exception.BizException;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.AiChatRequest;
import com.inkspace.service.AiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AI 接口：摘要 / 自动标签 / 周报为普通接口，问答为 SSE 流式。
 * SSE 事件数据统一用 JSON 字符串，避免正文里的换行破坏 SSE 协议。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor aiExecutor;
    private final AuditRecorder auditRecorder;

    public AiController(AiService aiService,
                        ObjectMapper objectMapper,
                        @Qualifier("aiExecutor") ThreadPoolTaskExecutor aiExecutor,
                        AuditRecorder auditRecorder) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.aiExecutor = aiExecutor;
        this.auditRecorder = auditRecorder;
    }

    @PostMapping("/summarize/{noteId}")
    @OperationLog(action = "AI_SUMMARIZE", resourceType = "NOTE", resourceId = "#noteId")
    public Result<String> summarize(@PathVariable Long noteId) {
        return Result.ok(aiService.summarize(CurrentUser.id(), noteId));
    }

    @PostMapping("/tags/{noteId}")
    @OperationLog(action = "AI_TAGS", resourceType = "NOTE", resourceId = "#noteId")
    public Result<List<String>> autoTags(@PathVariable Long noteId,
                                         @RequestParam(defaultValue = "false") boolean apply) {
        return Result.ok(aiService.autoTags(CurrentUser.id(), noteId, apply));
    }

    @PostMapping("/weekly")
    @OperationLog(action = "AI_WEEKLY", resourceType = "USER")
    public Result<String> weekly() {
        return Result.ok(aiService.weekly(CurrentUser.id()));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request) {
        Long userId = CurrentUser.id();
        String question = request.getQuestion();
        SseEmitter emitter = new SseEmitter(0L);

        // 不能依赖 @OperationLog：切面在 emit 返回时就写日志，此时回答还没开始生成，
        // 记的是假的"成功"。这里捕获上下文快照，等流真正结束再按结果落库。
        AuditRecorder.AuditContext auditContext = auditRecorder.capture();

        aiExecutor.execute(() -> {
            boolean ok = false;
            try {
                aiService.ask(userId, question,
                        citations -> sendJson(emitter, "citations", citations),
                        delta -> sendJson(emitter, "delta", Map.of("text", delta)));
                sendJson(emitter, "done", Map.of("ok", true));
                ok = true;
                emitter.complete();
            } catch (Exception e) {
                log.warn("AI 问答失败 userId={}", userId, e);
                try {
                    // 把可预期失败（比如模型名写错）的原因带给用户，其余情况给通用提示
                    String message = e instanceof BizException biz && biz.getMessage() != null
                            ? biz.getMessage() : "AI 服务暂时不可用";
                    sendJson(emitter, "error", Map.of("message", message));
                } catch (Exception ignored) {
                    // 客户端可能已经断开
                }
                emitter.complete();
            } finally {
                auditContext.write(auditRecorder.mapper(), "AI_QA", "USER", null,
                        ok ? "" : "error=AskFailed");
            }
        });
        return emitter;
    }

    private void sendJson(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 推送失败", e);
        }
    }
}
