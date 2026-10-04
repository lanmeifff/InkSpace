package com.inkspace.controller;

import com.inkspace.common.api.Result;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.ShareCreateRequest;
import com.inkspace.service.ShareService;
import com.inkspace.vo.PublicNoteVO;
import com.inkspace.vo.ShareVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分享接口：创建/关闭需要登录，GET /{token} 公开。
 */
@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @OperationLog(action = "SHARE_CREATE", resourceType = "SHARE", detail = "#request.noteId")
    public Result<ShareVO> create(@Valid @RequestBody ShareCreateRequest request) {
        return Result.ok(shareService.create(CurrentUser.id(), request));
    }

    @DeleteMapping("/{id}")
    @OperationLog(action = "SHARE_CLOSE", resourceType = "SHARE", resourceId = "#id")
    public Result<Void> close(@PathVariable Long id) {
        shareService.close(CurrentUser.id(), id);
        return Result.ok();
    }

    @GetMapping("/{token}")
    public Result<PublicNoteVO> publicGet(@PathVariable String token) {
        return Result.ok(shareService.publicGet(token));
    }
}
