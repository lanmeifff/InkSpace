package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建笔记。
 */
public class NoteCreateRequest {

    private Long notebookId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 200000, message = "正文过长")
    private String content;

    @Pattern(regexp = "manual|clip|import", message = "kind 只能是 manual/clip/import")
    private String kind;

    @Pattern(regexp = "draft|normal|archive|inbox", message = "status 只能是 draft/normal/archive/inbox")
    private String status;

    @Size(max = 512, message = "来源链接过长")
    private String sourceUrl;

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

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
