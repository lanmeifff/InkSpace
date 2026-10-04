package com.inkspace.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应：不直接暴露 MyBatis-Plus 的 Page 结构，避免持久层细节泄漏到接口。
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(IPage<T> source) {
        PageResult<T> result = new PageResult<>();
        result.setList(source.getRecords());
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        return result;
    }

    /** 由实体分页映射为 VO 分页 */
    public static <E, T> PageResult<T> of(IPage<E> source, Function<E, T> mapper) {
        PageResult<T> result = new PageResult<>();
        result.setList(source.getRecords().stream().map(mapper).toList());
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        return result;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
