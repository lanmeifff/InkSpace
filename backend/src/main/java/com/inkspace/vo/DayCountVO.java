package com.inkspace.vo;

/**
 * 热力图 / 趋势图的一天计数。
 */
public class DayCountVO {

    private String date;
    private long count;

    public DayCountVO() {
    }

    public DayCountVO(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
