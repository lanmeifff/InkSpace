package com.inkspace.controller;

import com.inkspace.common.ai.AiClientConfig;
import com.inkspace.common.ai.LlmPing;
import com.inkspace.common.api.Result;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.AiConfigRequest;
import com.inkspace.service.AiConfigService;
import com.inkspace.vo.AiConfigVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自带的 AI 接入配置：读 / 存 / 清空 / 测试连接。
 * 接口地址与模型名会被审计，API Key 绝不进日志，也绝不回传明文。
 */
@RestController
@RequestMapping("/api/v1/ai/config")
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final LlmPing llmPing;

    public AiConfigController(AiConfigService aiConfigService, LlmPing llmPing) {
        this.aiConfigService = aiConfigService;
        this.llmPing = llmPing;
    }

    @GetMapping
    public Result<AiConfigVO> get() {
        return Result.ok(aiConfigService.get(CurrentUser.id()));
    }

    @PutMapping
    @OperationLog(action = "AI_CONFIG_SAVE", resourceType = "USER", detail = "#request.model")
    public Result<AiConfigVO> save(@Valid @RequestBody AiConfigRequest request) {
        return Result.ok(aiConfigService.save(CurrentUser.id(), request));
    }

    @DeleteMapping
    @OperationLog(action = "AI_CONFIG_DELETE", resourceType = "USER")
    public Result<Void> delete() {
        aiConfigService.delete(CurrentUser.id());
        return Result.ok();
    }

    /** 用保存好的配置发一句 ping，确认地址/Key/模型三者确实能通 */
    @PostMapping("/test")
    @OperationLog(action = "AI_CONFIG_TEST", resourceType = "USER")
    public Result<String> test() {
        AiClientConfig config = aiConfigService.resolve(CurrentUser.id());
        return Result.ok(llmPing.ping(config));
    }
}
