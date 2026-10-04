package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 笔记本新建/修改请求。
 */
public class NotebookRequest {

    @NotBlank(message = "笔记本名不能为空")
    @Size(max = 32, message = "笔记本名不能超过 32 字")
    private String name;

    @Size(max = 16, message = "图标标识过长")
    private String icon;

    private Integer sortOrder;

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
}
