package com.inkspace.dto;

/**
 * 笔记列表查询条件（GET 参数绑定）。
 * size 上限 50，避免前端一次性拉爆数据库。
 */
public class NoteQuery {

    private static final int MAX_SIZE = 50;

    private long page = 1;
    private long size = 20;
    private Long notebookId;
    private Long tagId;
    private String keyword;
    private String status;
    private Boolean favorite;

    public long getPage() {
        return page < 1 ? 1 : page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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
}
