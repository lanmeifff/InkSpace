package com.inkspace.vo;

import com.inkspace.domain.entity.Note;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记视图：列表用 summary（不含正文，只给摘要），详情用 detail（含正文与标签）。
 */
public class NoteVO {

    private static final int EXCERPT_LENGTH = 120;

    private Long id;
    private Long notebookId;
    private String title;
    private String excerpt;
    private String content;
    private String kind;
    private String status;
    private Boolean favorite;
    private Boolean isPublic;
    private LocalDateTime publishedAt;
    private String sourceUrl;
    private Integer version;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 列表项：不含正文 */
    public static NoteVO summary(Note note) {
        NoteVO vo = base(note);
        String plain = note.getContentPlain() == null ? "" : note.getContentPlain();
        vo.setExcerpt(plain.length() > EXCERPT_LENGTH ? plain.substring(0, EXCERPT_LENGTH) + "…" : plain);
        return vo;
    }

    /** 详情：含正文与标签 */
    public static NoteVO detail(Note note, List<String> tags) {
        NoteVO vo = base(note);
        vo.setContent(note.getContent());
        vo.setTags(tags);
        return vo;
    }

    private static NoteVO base(Note note) {
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        vo.setNotebookId(note.getNotebookId());
        vo.setTitle(note.getTitle());
        vo.setKind(note.getKind());
        vo.setStatus(note.getStatus());
        vo.setFavorite(note.getIsFavorite());
        vo.setIsPublic(note.getIsPublic());
        vo.setPublishedAt(note.getPublishedAt());
        vo.setSourceUrl(note.getSourceUrl());
        vo.setVersion(note.getVersion());
        vo.setCreatedAt(note.getCreatedAt());
        vo.setUpdatedAt(note.getUpdatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
