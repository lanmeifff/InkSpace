package com.inkspace.common.util;

import org.springframework.util.StringUtils;

/**
 * 检索结果摘要与关键词高亮：在纯文本里截取命中位置附近的一段，并把关键词包成 &lt;em&gt;。
 * 先做 HTML 转义再插入标签，避免笔记内容里的标签造成 XSS。
 */
public final class HighlightUtil {

    private static final int DEFAULT_RADIUS = 60;
    private static final String ELLIPSIS = "…";

    private HighlightUtil() {
    }

    public static String snippet(String plain, String keyword) {
        return snippet(plain, keyword, DEFAULT_RADIUS);
    }

    public static String snippet(String plain, String keyword, int radius) {
        if (!StringUtils.hasText(plain)) {
            return "";
        }
        String escaped = escapeHtml(plain);
        if (!StringUtils.hasText(keyword)) {
            return escaped.length() > radius * 2 ? escaped.substring(0, radius * 2) + ELLIPSIS : escaped;
        }

        String escapedKeyword = escapeHtml(keyword.trim());
        int index = escaped.toLowerCase().indexOf(escapedKeyword.toLowerCase());
        if (index < 0) {
            return escaped.length() > radius * 2 ? escaped.substring(0, radius * 2) + ELLIPSIS : escaped;
        }

        int start = Math.max(0, index - radius);
        int end = Math.min(escaped.length(), index + escapedKeyword.length() + radius);
        String window = escaped.substring(start, end);
        // 只高亮窗口内的关键词（大小写不敏感替换）
        String highlighted = window.replaceAll("(?i)" + java.util.regex.Pattern.quote(escapedKeyword),
                "<em>" + escapedKeyword + "</em>");
        return (start > 0 ? ELLIPSIS : "") + highlighted + (end < escaped.length() ? ELLIPSIS : "");
    }

    public static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
