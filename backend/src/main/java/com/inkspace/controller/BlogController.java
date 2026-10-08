package com.inkspace.controller;

import com.inkspace.common.api.PageResult;
import com.inkspace.common.api.Result;
import com.inkspace.service.BlogService;
import com.inkspace.vo.BlogPostDetailVO;
import com.inkspace.vo.BlogPostVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开博客接口：全部免登录，只读。
 * 内容来自 note 表 is_public = 1 的笔记（在编辑器里通过 /notes/{id}/publish 开关）。
 */
@RestController
@RequestMapping("/api/v1/blog")
public class BlogController {

    private static final int MAX_PAGE_SIZE = 50;

    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    @GetMapping
    public Result<PageResult<BlogPostVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String tag) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return Result.ok(blogService.list(safePage, safeSize, tag));
    }

    @GetMapping("/sidebar")
    public Result<Map<String, Object>> sidebar() {
        return Result.ok(blogService.sidebar());
    }

    @GetMapping("/{id}")
    public Result<BlogPostDetailVO> detail(@PathVariable Long id) {
        return Result.ok(blogService.detail(id));
    }
}
