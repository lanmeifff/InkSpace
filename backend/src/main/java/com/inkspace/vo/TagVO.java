package com.inkspace.vo;

import com.inkspace.domain.entity.Tag;

/**
 * 标签视图。
 */
public class TagVO {

    private Long id;
    private String name;

    public static TagVO from(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
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
}
