package com.inkspace.dto;

/**
 * 公开 / 取消公开到博客。
 */
public class PublishRequest {

    private boolean published;

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }
}
