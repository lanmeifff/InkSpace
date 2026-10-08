package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkspace.domain.entity.NoteTag;
import com.inkspace.domain.entity.Tag;
import com.inkspace.mapper.NoteTagMapper;
import com.inkspace.mapper.TagMapper;
import com.inkspace.vo.TagVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签：按用户隔离，重名复用（find-or-create）。
 */
@Service
public class TagService {

    private final TagMapper tagMapper;
    private final NoteTagMapper noteTagMapper;

    public TagService(TagMapper tagMapper, NoteTagMapper noteTagMapper) {
        this.tagMapper = tagMapper;
        this.noteTagMapper = noteTagMapper;
    }

    public List<TagVO> list(Long userId, String keyword) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .orderByAsc(Tag::getName);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Tag::getName, keyword.trim());
        }
        return tagMapper.selectList(wrapper).stream().map(TagVO::from).toList();
    }

    /** 取某笔记的标签名列表 */
    public List<String> namesOfNote(Long noteId) {
        List<Long> tagIds = noteTagMapper.selectList(new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getNoteId, noteId))
                .stream().map(NoteTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectBatchIds(tagIds).stream().map(Tag::getName).toList();
    }

    /**
     * 批量取多篇笔记的标签名：博客列表一页十几篇，
     * 逐篇查会变成 2N 次查询，这里固定 2 次。
     */
    public Map<Long, List<String>> namesOfNotes(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<NoteTag> links = noteTagMapper.selectList(new LambdaQueryWrapper<NoteTag>()
                .in(NoteTag::getNoteId, noteIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameById = tagMapper.selectBatchIds(
                        links.stream().map(NoteTag::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> result = new HashMap<>();
        for (NoteTag link : links) {
            String name = nameById.get(link.getTagId());
            if (name != null) {
                result.computeIfAbsent(link.getNoteId(), key -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    /** 全覆盖设置：先删旧关联，再按去重后的标签名逐个 find-or-create 并关联 */
    public List<String> replaceNoteTags(Long userId, Long noteId, List<String> names) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : names) {
            if (StringUtils.hasText(raw)) {
                distinct.add(raw.trim());
            }
        }

        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        for (String name : distinct) {
            Tag tag = findOrCreate(userId, name);
            noteTagMapper.insert(new NoteTag(noteId, tag.getId()));
        }
        return List.copyOf(distinct);
    }

    private Tag findOrCreate(Long userId, String name) {
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, name));
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException e) {
            // 并发下同名标签被其他请求先插入：回查即可
            return tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getUserId, userId)
                    .eq(Tag::getName, name));
        }
    }
}
