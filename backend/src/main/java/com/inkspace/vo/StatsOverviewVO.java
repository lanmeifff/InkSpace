package com.inkspace.vo;

import java.util.List;

/**
 * 仪表盘总览。
 */
public class StatsOverviewVO {

    private long noteCount;
    private long favoriteCount;
    private long wordCount;
    private long notebookCount;
    private long tagCount;
    private List<DayCountVO> last7Days;

    public long getNoteCount() {
        return noteCount;
    }

    public void setNoteCount(long noteCount) {
        this.noteCount = noteCount;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getWordCount() {
        return wordCount;
    }

    public void setWordCount(long wordCount) {
        this.wordCount = wordCount;
    }

    public long getNotebookCount() {
        return notebookCount;
    }

    public void setNotebookCount(long notebookCount) {
        this.notebookCount = notebookCount;
    }

    public long getTagCount() {
        return tagCount;
    }

    public void setTagCount(long tagCount) {
        this.tagCount = tagCount;
    }

    public List<DayCountVO> getLast7Days() {
        return last7Days;
    }

    public void setLast7Days(List<DayCountVO> last7Days) {
        this.last7Days = last7Days;
    }
}
