package com.inkspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建分享：expireHours 不传默认 168 小时（7 天）。
 */
public class ShareCreateRequest {

    @NotNull(message = "noteId 不能为空")
    private Long noteId;

    @Min(value = 1, message = "有效期至少 1 小时")
    @Max(value = 8760, message = "有效期最多 8760 小时（一年）")
    private Integer expireHours;

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Integer getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(Integer expireHours) {
        this.expireHours = expireHours;
    }
}
