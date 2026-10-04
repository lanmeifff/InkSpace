package com.inkspace.vo;

import com.inkspace.domain.entity.Share;

import java.time.LocalDateTime;

/**
 * 分享记录视图（仅创建者可见）。
 */
public class ShareVO {

    private Long id;
    private Long noteId;
    private String token;
    private String shareUrl;
    private LocalDateTime expireAt;
    private Boolean closed;
    private Integer viewCount;
    private LocalDateTime createdAt;

    public static ShareVO from(Share share, String shareBaseUrl) {
        ShareVO vo = new ShareVO();
        vo.setId(share.getId());
        vo.setNoteId(share.getNoteId());
        vo.setToken(share.getToken());
        vo.setShareUrl(shareBaseUrl + share.getToken());
        vo.setExpireAt(share.getExpireAt());
        vo.setClosed(share.getClosed());
        vo.setViewCount(share.getViewCount());
        vo.setCreatedAt(share.getCreatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Boolean getClosed() {
        return closed;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
