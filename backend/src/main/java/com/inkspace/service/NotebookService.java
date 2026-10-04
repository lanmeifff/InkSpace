package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.domain.entity.Notebook;
import com.inkspace.dto.NotebookRequest;
import com.inkspace.mapper.NotebookMapper;
import com.inkspace.vo.NotebookVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记本。所有读写都带 user_id 条件——别人的资源在本用户视角下"不存在"（返回 404）。
 */
@Service
public class NotebookService {

    private final NotebookMapper notebookMapper;

    public NotebookService(NotebookMapper notebookMapper) {
        this.notebookMapper = notebookMapper;
    }

    public List<NotebookVO> list(Long userId) {
        return notebookMapper.selectList(new LambdaQueryWrapper<Notebook>()
                        .eq(Notebook::getUserId, userId)
                        .isNull(Notebook::getDeletedAt)
                        .orderByAsc(Notebook::getSortOrder)
                        .orderByDesc(Notebook::getId))
                .stream().map(NotebookVO::from).toList();
    }

    public NotebookVO create(Long userId, NotebookRequest request) {
        Notebook notebook = new Notebook();
        notebook.setUserId(userId);
        notebook.setName(request.getName().trim());
        notebook.setIcon(request.getIcon() == null ? "" : request.getIcon());
        notebook.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        notebookMapper.insert(notebook);
        return NotebookVO.from(notebookMapper.selectById(notebook.getId()));
    }

    public NotebookVO update(Long userId, Long id, NotebookRequest request) {
        Notebook notebook = requireOwned(userId, id);
        notebook.setName(request.getName().trim());
        if (request.getIcon() != null) {
            notebook.setIcon(request.getIcon());
        }
        if (request.getSortOrder() != null) {
            notebook.setSortOrder(request.getSortOrder());
        }
        notebookMapper.updateById(notebook);
        return NotebookVO.from(notebookMapper.selectById(id));
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        Notebook update = new Notebook();
        update.setDeletedAt(LocalDateTime.now());
        notebookMapper.update(update, new LambdaQueryWrapper<Notebook>()
                .eq(Notebook::getId, id)
                .eq(Notebook::getUserId, userId));
    }

    /** 校验笔记本属于当前用户；不存在或不属于 → 404 */
    public Notebook requireOwned(Long userId, Long notebookId) {
        Notebook notebook = notebookMapper.selectOne(new LambdaQueryWrapper<Notebook>()
                .eq(Notebook::getId, notebookId)
                .eq(Notebook::getUserId, userId)
                .isNull(Notebook::getDeletedAt));
        if (notebook == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return notebook;
    }
}
