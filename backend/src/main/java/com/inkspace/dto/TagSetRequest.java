package com.inkspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 设置笔记标签（全量覆盖）。
 */
public class TagSetRequest {

    @NotNull(message = "tags 不能为空")
    @Size(max = 10, message = "单篇笔记最多 10 个标签")
    private List<@NotBlank(message = "标签名不能为空") @Size(max = 32, message = "标签名不能超过 32 字") String> tags;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
