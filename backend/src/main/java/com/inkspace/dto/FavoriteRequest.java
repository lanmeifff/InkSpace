package com.inkspace.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 收藏开关（显式传值，天然幂等，比 toggle 更安全）。
 */
public class FavoriteRequest {

    @NotNull(message = "favorite 不能为空")
    private Boolean favorite;

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }
}
