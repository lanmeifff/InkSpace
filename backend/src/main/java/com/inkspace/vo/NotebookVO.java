package com.inkspace.vo;

import com.inkspace.domain.entity.Notebook;

import java.time.LocalDateTime;

/**
 * 笔记本视图。
 */
public class NotebookVO {

    private Long id;
    private String name;
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotebookVO from(Notebook notebook) {
        NotebookVO vo = new NotebookVO();
        vo.setId(notebook.getId());
        vo.setName(notebook.getName());
        vo.setIcon(notebook.getIcon());
        vo.setSortOrder(notebook.getSortOrder());
        vo.setCreatedAt(notebook.getCreatedAt());
        vo.setUpdatedAt(notebook.getUpdatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
