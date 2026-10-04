package com.inkspace.common.util;

/**
 * Markdown 去格式：把原文转成纯文本存入 note.content_plain，供后续全文检索使用。
 * 只做轻量处理（不追求完美），够检索召回即可。
 */
public final class MarkdownUtil {

    private MarkdownUtil() {
    }

    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown
                // 代码块标记
                .replaceAll("```[\\s\\S]*?```", " ")
                // 行内代码
                .replaceAll("`([^`]*)`", "$1")
                // 图片
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                // 链接保留文字
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                // 标题、引用、列表符号
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s{0,3}[-*+]\\s+", "")
                .replaceAll("(?m)^\\s{0,3}\\d+\\.\\s+", "")
                // 强调符号
                .replaceAll("[*_~]{1,3}", "")
                // HTML 标签
                .replaceAll("<[^>]+>", " ");
        return text.replaceAll("\\s+", " ").trim();
    }
}
