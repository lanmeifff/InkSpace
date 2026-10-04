package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.api.PageResult;
import com.inkspace.common.exception.BizException;
import com.inkspace.common.util.HighlightUtil;
import com.inkspace.common.util.MarkdownUtil;
import com.inkspace.domain.entity.Note;
import com.inkspace.domain.entity.NoteTag;
import com.inkspace.dto.NoteCreateRequest;
import com.inkspace.dto.NoteQuery;
import com.inkspace.dto.NoteUpdateRequest;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.mapper.NoteTagMapper;
import com.inkspace.vo.NoteVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记核心服务。
 * 查询都带 user_id，越权按 404 处理；更新走 version 乐观锁；
 * 列表不取 content，用 content_plain 生成摘要。
 */
@Service
public class NoteService {

    private static final String KIND_MANUAL = "manual";
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_ARCHIVE = "archive";
    private static final String STATUS_DRAFT = "draft";

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final NotebookService notebookService;
    private final TagService tagService;
    private final StatsService statsService;

    public NoteService(NoteMapper noteMapper,
                       NoteTagMapper noteTagMapper,
                       NotebookService notebookService,
                       TagService tagService,
                       StatsService statsService) {
        this.noteMapper = noteMapper;
        this.noteTagMapper = noteTagMapper;
        this.notebookService = notebookService;
        this.tagService = tagService;
        this.statsService = statsService;
    }

    public PageResult<NoteVO> list(Long userId, NoteQuery query) {
        // 带关键词 → 走 ngram 全文检索（标题+正文，相关性排序 + 高亮）
        if (StringUtils.hasText(query.getKeyword())) {
            return search(userId, query);
        }

        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt)
                .select(Note::getId, Note::getNotebookId, Note::getTitle, Note::getContentPlain,
                        Note::getKind, Note::getStatus, Note::getIsFavorite, Note::getSourceUrl,
                        Note::getVersion, Note::getCreatedAt, Note::getUpdatedAt)
                .orderByDesc(Note::getUpdatedAt)
                .orderByDesc(Note::getId);

        if (query.getNotebookId() != null) {
            wrapper.eq(Note::getNotebookId, query.getNotebookId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Note::getStatus, query.getStatus());
        }
        if (query.getFavorite() != null) {
            wrapper.eq(Note::getIsFavorite, query.getFavorite());
        }
        List<Long> tagNoteIds = resolveTagNoteIds(query.getTagId());
        if (tagNoteIds != null) {
            if (tagNoteIds.isEmpty()) {
                return PageResult.of(new Page<>(query.getPage(), query.getSize()));
            }
            wrapper.in(Note::getId, tagNoteIds);
        }

        Page<Note> page = noteMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page, NoteVO::summary);
    }

    /** 全文检索：ngram FULLTEXT 命中标题或正文，按相关性倒序，摘要里高亮关键词 */
    private PageResult<NoteVO> search(Long userId, NoteQuery query) {
        List<Long> noteIds = resolveTagNoteIds(query.getTagId());
        if (noteIds != null && noteIds.isEmpty()) {
            return PageResult.of(new Page<>(query.getPage(), query.getSize()));
        }

        Page<Note> page = new Page<>(query.getPage(), query.getSize());
        Page<Note> result = (Page<Note>) noteMapper.search(page, userId, query.getKeyword().trim(),
                query.getNotebookId(), query.getStatus(), query.getFavorite(), noteIds);
        return PageResult.of(result, note -> {
            NoteVO vo = NoteVO.summary(note);
            vo.setExcerpt(HighlightUtil.snippet(note.getContentPlain(), query.getKeyword().trim()));
            return vo;
        });
    }

    /** 标签筛选：解析出该标签下的笔记 id 集合；tagId 为空返回 null（表示不按标签过滤） */
    private List<Long> resolveTagNoteIds(Long tagId) {
        if (tagId == null) {
            return null;
        }
        return noteTagMapper.selectList(new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getTagId, tagId))
                .stream().map(NoteTag::getNoteId).toList();
    }

    public PageResult<NoteVO> trash(Long userId, NoteQuery query) {
        Page<Note> page = noteMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .isNotNull(Note::getDeletedAt)
                        .orderByDesc(Note::getDeletedAt));
        return PageResult.of(page, NoteVO::summary);
    }

    public NoteVO detail(Long userId, Long noteId) {
        Note note = requireOwned(userId, noteId);
        return NoteVO.detail(note, tagService.namesOfNote(noteId));
    }

    @Transactional
    public NoteVO create(Long userId, NoteCreateRequest request) {
        if (request.getNotebookId() != null) {
            notebookService.requireOwned(userId, request.getNotebookId());
        }
        Note note = new Note();
        note.setUserId(userId);
        note.setNotebookId(request.getNotebookId());
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent() == null ? "" : request.getContent());
        note.setContentPlain(MarkdownUtil.toPlainText(note.getContent()));
        note.setKind(StringUtils.hasText(request.getKind()) ? request.getKind() : KIND_MANUAL);
        note.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_NORMAL);
        note.setIsFavorite(false);
        note.setSourceUrl(request.getSourceUrl() == null ? "" : request.getSourceUrl());
        note.setVersion(1);
        noteMapper.insert(note);
        statsService.evict(userId);
        return NoteVO.detail(noteMapper.selectById(note.getId()), List.of());
    }

    @Transactional
    public NoteVO update(Long userId, Long noteId, NoteUpdateRequest request) {
        requireOwned(userId, noteId);
        if (request.getNotebookId() != null) {
            notebookService.requireOwned(userId, request.getNotebookId());
        }

        Note update = new Note();
        update.setTitle(request.getTitle().trim());
        update.setContent(request.getContent() == null ? "" : request.getContent());
        update.setContentPlain(MarkdownUtil.toPlainText(update.getContent()));
        update.setNotebookId(request.getNotebookId());
        if (StringUtils.hasText(request.getStatus())) {
            update.setStatus(request.getStatus());
        }
        update.setVersion(request.getVersion() + 1);

        // 乐观锁：只有客户端版本号仍等于当前值时才更新成功
        int rows = noteMapper.update(update, new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .eq(Note::getVersion, request.getVersion())
                .isNull(Note::getDeletedAt));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        statsService.evict(userId);
        Note saved = noteMapper.selectById(noteId);
        return NoteVO.detail(saved, tagService.namesOfNote(noteId));
    }

    public NoteVO setFavorite(Long userId, Long noteId, boolean favorite) {
        requireOwned(userId, noteId);
        Note update = new Note();
        update.setIsFavorite(favorite);
        noteMapper.update(update, new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt));
        statsService.evict(userId);
        return NoteVO.detail(noteMapper.selectById(noteId), tagService.namesOfNote(noteId));
    }

    public NoteVO setArchived(Long userId, Long noteId, boolean archived) {
        Note current = requireOwned(userId, noteId);
        Note update = new Note();
        // 归档只影响 normal/archive 两态：草稿不被归档动作覆盖
        if (archived) {
            update.setStatus(STATUS_ARCHIVE);
        } else {
            update.setStatus(STATUS_DRAFT.equals(current.getStatus()) ? STATUS_DRAFT : STATUS_NORMAL);
        }
        noteMapper.update(update, new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt));
        statsService.evict(userId);
        return NoteVO.detail(noteMapper.selectById(noteId), tagService.namesOfNote(noteId));
    }

    @Transactional
    public List<String> setTags(Long userId, Long noteId, List<String> names) {
        requireOwned(userId, noteId);
        List<String> result = tagService.replaceNoteTags(userId, noteId, names);
        statsService.evict(userId);
        return result;
    }

    /** 软删除 → 进回收站 */
    public void softDelete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        Note update = new Note();
        update.setDeletedAt(LocalDateTime.now());
        noteMapper.update(update, new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt));
        statsService.evict(userId);
    }

    public void restore(Long userId, Long noteId) {
        Note note = noteMapper.selectOne(new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNotNull(Note::getDeletedAt));
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // MP 默认忽略 null 字段，置空 deleted_at 必须用 UpdateWrapper 显式 set
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .set(Note::getDeletedAt, null)
                .set(Note::getUpdatedAt, LocalDateTime.now()));
        statsService.evict(userId);
    }

    /** 回收站内物理删除：连带清理标签关联（事务） */
    @Transactional
    public void physicalDelete(Long userId, Long noteId) {
        Note note = noteMapper.selectOne(new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNotNull(Note::getDeletedAt));
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        noteMapper.deleteById(noteId);
        statsService.evict(userId);
    }

    /** 归属校验：不存在 / 不属于当前用户 / 已删除 → 统一 404 */
    public Note requireOwned(Long userId, Long noteId) {
        Note note = noteMapper.selectOne(new LambdaQueryWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getUserId, userId)
                .isNull(Note::getDeletedAt));
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return note;
    }
}
