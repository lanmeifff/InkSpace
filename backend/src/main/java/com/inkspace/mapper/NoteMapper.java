package com.inkspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.inkspace.domain.entity.Note;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 当前用户笔记的全文检索（ngram FULLTEXT），按相关性倒序，并支持与列表一致的筛选条件。
     * XML 见 resources/mapper/NoteMapper.xml。
     *
     * @param noteIds 标签筛选预先解析出的笔记 id 集合；为 null 表示不按标签过滤
     */
    IPage<Note> search(IPage<Note> page,
                       @Param("userId") Long userId,
                       @Param("keyword") String keyword,
                       @Param("notebookId") Long notebookId,
                       @Param("status") String status,
                       @Param("favorite") Boolean favorite,
                       @Param("noteIds") List<Long> noteIds);
}
