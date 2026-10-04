package com.inkspace.controller;

import com.inkspace.common.api.Result;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.dto.NotebookRequest;
import com.inkspace.service.NotebookService;
import com.inkspace.vo.NotebookVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 笔记本接口。
 */
@RestController
@RequestMapping("/api/v1/notebooks")
public class NotebookController {

    private final NotebookService notebookService;

    public NotebookController(NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @GetMapping
    public Result<List<NotebookVO>> list() {
        return Result.ok(notebookService.list(CurrentUser.id()));
    }

    @PostMapping
    public Result<NotebookVO> create(@Valid @RequestBody NotebookRequest request) {
        return Result.ok(notebookService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{id}")
    public Result<NotebookVO> update(@PathVariable Long id, @Valid @RequestBody NotebookRequest request) {
        return Result.ok(notebookService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notebookService.delete(CurrentUser.id(), id);
        return Result.ok();
    }
}
