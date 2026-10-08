package com.inkspace.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 博客列表项 / 侧栏条目：只暴露公开阅读需要的字段，
 * 不含 user_id、notebook_id、version 等内部信息。
 *
 * @param excerpt  纯文本摘要（列表用，不返回正文）
 * @param readTime 预计阅读分钟数
 */
public record BlogPostVO(
        Long id,
        String title,
        String excerpt,
        LocalDateTime publishedAt,
        List<String> tags,
        String author,
        int readTime,
        boolean featured) {
}
