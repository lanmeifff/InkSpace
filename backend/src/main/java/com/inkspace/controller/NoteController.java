package com.inkspace.controller;

import com.inkspace.common.api.PageResult;
import com.inkspace.common.api.Result;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.ArchiveRequest;
import com.inkspace.dto.ClipRequest;
import com.inkspace.dto.FavoriteRequest;
import com.inkspace.dto.NoteCreateRequest;
import com.inkspace.dto.NoteQuery;
import com.inkspace.dto.NoteUpdateRequest;
import com.inkspace.dto.PublishRequest;
import com.inkspace.dto.TagSetRequest;
import com.inkspace.service.ClipService;
import com.inkspace.service.ImportService;
import com.inkspace.service.NoteService;
import com.inkspace.vo.NoteVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 笔记接口（CRUD + 收藏/归档/回收站/标签）。
 */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;
    private final ClipService clipService;
    private final ImportService importService;

    public NoteController(NoteService noteService, ClipService clipService, ImportService importService) {
        this.noteService = noteService;
        this.clipService = clipService;
        this.importService = importService;
    }

    @GetMapping
    public Result<PageResult<NoteVO>> list(NoteQuery query) {
        return Result.ok(noteService.list(CurrentUser.id(), query));
    }

    @GetMapping("/trash")
    public Result<PageResult<NoteVO>> trash(NoteQuery query) {
        return Result.ok(noteService.trash(CurrentUser.id(), query));
    }

    @GetMapping("/{id}")
    public Result<NoteVO> detail(@PathVariable Long id) {
        return Result.ok(noteService.detail(CurrentUser.id(), id));
    }

    @PostMapping
    public Result<NoteVO> create(@Valid @RequestBody NoteCreateRequest request) {
        return Result.ok(noteService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{id}")
    public Result<NoteVO> update(@PathVariable Long id, @Valid @RequestBody NoteUpdateRequest request) {
        return Result.ok(noteService.update(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/favorite")
    public Result<NoteVO> favorite(@PathVariable Long id, @Valid @RequestBody FavoriteRequest request) {
        return Result.ok(noteService.setFavorite(CurrentUser.id(), id, request.getFavorite()));
    }

    @PutMapping("/{id}/archive")
    public Result<NoteVO> archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest request) {
        return Result.ok(noteService.setArchived(CurrentUser.id(), id, request.getArchived()));
    }

    /** 公开 / 取消公开到博客（免登录可读） */
    @PutMapping("/{id}/publish")
    @OperationLog(action = "NOTE_PUBLISH", resourceType = "NOTE", resourceId = "#id")
    public Result<NoteVO> publish(@PathVariable Long id, @Valid @RequestBody PublishRequest request) {
        return Result.ok(noteService.setPublished(CurrentUser.id(), id, request.isPublished()));
    }

    @PutMapping("/{id}/tags")
    @OperationLog(action = "NOTE_TAG_SET", resourceType = "NOTE", resourceId = "#id")
    public Result<List<String>> setTags(@PathVariable Long id, @Valid @RequestBody TagSetRequest request) {
        return Result.ok(noteService.setTags(CurrentUser.id(), id, request.getTags()));
    }

    /** URL 剪藏：抓取网页正文存为稍后读笔记 */
    @PostMapping("/clip")
    @OperationLog(action = "NOTE_CLIP", resourceType = "NOTE", detail = "#request.url")
    public Result<NoteVO> clip(@Valid @RequestBody ClipRequest request) {
        return Result.ok(clipService.clip(CurrentUser.id(), request));
    }

    /** Markdown 批量导入（Redis 锁防重复提交） */
    @PostMapping("/import")
    @OperationLog(action = "NOTE_IMPORT", resourceType = "NOTE")
    public Result<Integer> importMarkdown(@RequestParam("files") List<MultipartFile> files) {
        return Result.ok(importService.importMarkdown(CurrentUser.id(), files));
    }

    @DeleteMapping("/{id}")
    @OperationLog(action = "NOTE_DELETE", resourceType = "NOTE", resourceId = "#id")
    public Result<Void> softDelete(@PathVariable Long id) {
        noteService.softDelete(CurrentUser.id(), id);
        return Result.ok();
    }

    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        noteService.restore(CurrentUser.id(), id);
        return Result.ok();
    }

    @DeleteMapping("/trash/{id}")
    @OperationLog(action = "NOTE_PURGE", resourceType = "NOTE", resourceId = "#id")
    public Result<Void> physicalDelete(@PathVariable Long id) {
        noteService.physicalDelete(CurrentUser.id(), id);
        return Result.ok();
    }
}
