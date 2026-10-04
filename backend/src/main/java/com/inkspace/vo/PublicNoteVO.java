package com.inkspace.vo;

import java.time.LocalDateTime;

/**
 * 公开分享页返回：只暴露阅读所需字段（不含用户 id / 笔记本等内部信息）。
 */
public class PublicNoteVO {

    private String title;
    private String content;
    private LocalDateTime updatedAt;
    private Integer viewCount;

    public PublicNoteVO() {
    }

    public PublicNoteVO(String title, String content, LocalDateTime updatedAt, Integer viewCount) {
        this.title = title;
        this.content = content;
        this.updatedAt = updatedAt;
        this.viewCount = viewCount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }
}
