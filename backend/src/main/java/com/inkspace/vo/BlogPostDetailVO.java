package com.inkspace.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 博客文章详情：正文 + 元信息。仅对 is_public = 1 的笔记开放。
 *
 * @param updatedAt 最后更新时间（正文里的"更新于"）
 * @param publishedAt 首次公开时间（正文里的"发布于"）
 */
public record BlogPostDetailVO(
        Long id,
        String title,
        String content,
        List<String> tags,
        String author,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt,
        int readTime,
        String sourceUrl) {
}
