package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.api.PageResult;
import com.inkspace.common.exception.BizException;
import com.inkspace.domain.entity.Note;
import com.inkspace.domain.entity.User;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.mapper.UserMapper;
import com.inkspace.vo.BlogPostDetailVO;
import com.inkspace.vo.BlogPostVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公开博客：只读，数据源是 note 表里 is_public = 1 且未软删的笔记。
 *
 * 全部接口都不需要登录，因此这里刻意只输出阅读需要的字段（BlogPostVO / BlogPostDetailVO），
 * 不带 user_id、notebook_id、version 等内部信息。正文用笔记的 Markdown 原文，
 * 与分享页共用同一套渲染。
 */
@Service
public class BlogService {

    /** 列表摘要长度：杂志式列表用两行，比笔记列表的 120 字略长 */
    private static final int EXCERPT_LENGTH = 200;
    /** 中文阅读速度约 400 字/分钟 */
    private static final int WORDS_PER_MINUTE = 400;
    private static final int SIDEBAR_POPULAR = 5;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final TagService tagService;

    public BlogService(NoteMapper noteMapper, UserMapper userMapper, TagService tagService) {
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
        this.tagService = tagService;
    }

    /** 博客首页列表：按首次公开时间倒序 */
    public PageResult<BlogPostVO> list(int page, int size, String tag) {
        LambdaQueryWrapper<Note> wrapper = publicNotes()
                .orderByDesc(Note::getPublishedAt)
                .orderByDesc(Note::getId);
        if (StringUtils.hasText(tag)) {
            // 先按标签名找出笔记 id，再收窄查询；标签不存在时直接返回空页
            List<Long> ids = noteIdsWithTag(tag.trim());
            if (ids.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
            wrapper.in(Note::getId, ids);
        }
        Page<Note> result = noteMapper.selectPage(new Page<>(page, size), wrapper);
        // 标签与作者各批量取一次，避免逐篇查
        Map<Long, List<String>> tagMap = tagService.namesOfNotes(
                result.getRecords().stream().map(Note::getId).toList());
        Map<Long, String> authorCache = new HashMap<>();
        return PageResult.of(result, (Note note) -> toPost(note, tagMap, authorCache));
    }

    public BlogPostDetailVO detail(Long noteId) {
        Note note = noteMapper.selectOne(publicNotes().eq(Note::getId, noteId));
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new BlogPostDetailVO(
                note.getId(),
                note.getTitle(),
                note.getContent() == null ? "" : note.getContent(),
                tagService.namesOfNote(noteId),
                authorOf(note.getUserId()),
                note.getPublishedAt(),
                note.getUpdatedAt(),
                readTime(note.getContentPlain()),
                note.getSourceUrl());
    }

    /**
     * 侧栏数据：热门文章、标签云、按月归档、站点统计。
     * 数据量是个人博客级别（几十到几百篇），一次全量取回在内存里聚合比多次分组查询更简单。
     */
    public Map<String, Object> sidebar() {
        List<Note> published = noteMapper.selectList(publicNotes()
                .orderByDesc(Note::getPublishedAt));
        Map<Long, List<String>> tagMap = tagService.namesOfNotes(
                published.stream().map(Note::getId).toList());
        Map<Long, String> authorCache = new HashMap<>();
        Map<Long, BlogPostVO> postById = new LinkedHashMap<>();
        for (Note note : published) {
            postById.put(note.getId(), toPost(note, tagMap, authorCache));
        }

        // 热门 = 收藏数优先，其次正文更长（信息量更大）；并列时按公开时间新的在前
        List<BlogPostVO> popular = published.stream()
                .sorted(Comparator
                        .comparingInt((Note note) -> note.getIsFavorite() != null && note.getIsFavorite() ? 1 : 0)
                        .thenComparingInt((Note note) -> note.getContentPlain() == null ? 0 : note.getContentPlain().length())
                        .reversed())
                .limit(SIDEBAR_POPULAR)
                .map(note -> postById.get(note.getId()))
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, Long> tagCounts = new HashMap<>();
        for (List<String> names : tagMap.values()) {
            for (String name : names) {
                tagCounts.merge(name, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> tags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(24)
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "count", entry.getValue()))
                .toList();

        Map<String, Long> archiveCounts = new LinkedHashMap<>();
        published.stream()
                .map(Note::getPublishedAt)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .forEach(date -> archiveCounts.merge(YearMonth.from(date).format(MONTH), 1L, Long::sum));
        List<Map<String, Object>> archive = archiveCounts.entrySet().stream()
                .map(entry -> Map.<String, Object>of("month", entry.getKey(), "count", entry.getValue()))
                .toList();

        int totalWords = published.stream()
                .mapToInt(note -> note.getContentPlain() == null ? 0 : note.getContentPlain().length())
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("popular", popular);
        result.put("tags", tags);
        result.put("archive", archive);
        result.put("totalPosts", published.size());
        result.put("totalWords", totalWords);
        result.put("tagCount", tagCounts.size());
        return result;
    }

    // 内部工具

    private LambdaQueryWrapper<Note> publicNotes() {
        return new LambdaQueryWrapper<Note>()
                .eq(Note::getIsPublic, true)
                .isNull(Note::getDeletedAt);
    }

    /** 按标签名反查笔记 id：标签按用户隔离，这里跨用户按名字匹配（博客只有作者自己的内容） */
    private List<Long> noteIdsWithTag(String tagName) {
        List<Note> candidates = noteMapper.selectList(publicNotes().select(Note::getId));
        Map<Long, List<String>> tagMap = tagService.namesOfNotes(
                candidates.stream().map(Note::getId).toList());
        return candidates.stream()
                .filter(note -> tagMap.getOrDefault(note.getId(), List.of()).contains(tagName))
                .map(Note::getId)
                .toList();
    }

    /** 单篇实体 → 列表项；authorCache 由调用方复用，避免同一次请求里重复查作者 */
    private BlogPostVO toPost(Note note, Map<Long, List<String>> tagMap, Map<Long, String> authorCache) {
        return new BlogPostVO(
                note.getId(),
                note.getTitle(),
                excerpt(note.getContentPlain()),
                note.getPublishedAt(),
                tagMap.getOrDefault(note.getId(), List.of()),
                authorCache.computeIfAbsent(note.getUserId(), this::authorOf),
                readTime(note.getContentPlain()),
                Boolean.TRUE.equals(note.getIsFavorite()));
    }

    /** 作者显示名：优先昵称，其次用户名；用户被删则留空 */
    private String authorOf(Long userId) {
        if (userId == null) {
            return "";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private static String excerpt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return "";
        }
        String trimmed = plain.trim().replaceAll("\\s+", " ");
        return trimmed.length() > EXCERPT_LENGTH ? trimmed.substring(0, EXCERPT_LENGTH) + "…" : trimmed;
    }

    private static int readTime(String plain) {
        if (!StringUtils.hasText(plain)) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(plain.length() / (double) WORDS_PER_MINUTE));
    }
}
