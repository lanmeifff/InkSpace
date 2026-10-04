package com.inkspace.controller;

import com.inkspace.common.api.Result;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.service.TagService;
import com.inkspace.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public Result<List<TagVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(tagService.list(CurrentUser.id(), keyword));
    }
}
