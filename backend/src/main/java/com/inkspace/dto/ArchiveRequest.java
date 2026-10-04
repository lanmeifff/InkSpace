package com.inkspace.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 归档开关：true → status=archive，false → status=normal。
 */
public class ArchiveRequest {

    @NotNull(message = "archived 不能为空")
    private Boolean archived;

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }
}
