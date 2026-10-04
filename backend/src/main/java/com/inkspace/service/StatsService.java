package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.domain.entity.Note;
import com.inkspace.domain.entity.Notebook;
import com.inkspace.domain.entity.Tag;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.mapper.NotebookMapper;
import com.inkspace.mapper.TagMapper;
import com.inkspace.vo.DayCountVO;
import com.inkspace.vo.StatsOverviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计。
 *
 * Redis 场景：聚合结果缓存（写少读多），键 cache:stats:{userId} / cache:heatmap:{userId}，
 * 笔记发生写操作时主动删除（Cache Aside），再叠加 5 分钟 TTL 兜底。
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private static final String STATS_KEY = "cache:stats:";
    private static final String HEATMAP_KEY = "cache:heatmap:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final TagMapper tagMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StatsService(NoteMapper noteMapper,
                        NotebookMapper notebookMapper,
                        TagMapper tagMapper,
                        StringRedisTemplate redis,
                        ObjectMapper objectMapper) {
        this.noteMapper = noteMapper;
        this.notebookMapper = notebookMapper;
        this.tagMapper = tagMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public StatsOverviewVO overview(Long userId) {
        String key = STATS_KEY + userId;
        StatsOverviewVO cached = read(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setNoteCount(noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId).isNull(Note::getDeletedAt)));
        vo.setFavoriteCount(noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId).eq(Note::getIsFavorite, true).isNull(Note::getDeletedAt)));
        vo.setNotebookCount(notebookMapper.selectCount(new LambdaQueryWrapper<Notebook>()
                .eq(Notebook::getUserId, userId).isNull(Notebook::getDeletedAt)));
        vo.setTagCount(tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId)));
        vo.setWordCount(sumWordCount(userId));
        vo.setLast7Days(dailyCounts(userId, LocalDate.now().minusDays(6).atStartOfDay()));
        write(key, vo);
        return vo;
    }

    public List<DayCountVO> heatmap(Long userId, int weeks) {
        String key = HEATMAP_KEY + userId;
        List<DayCountVO> cached = read(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        List<DayCountVO> data = dailyCounts(userId,
                LocalDate.now().minusWeeks(weeks).atStartOfDay());
        write(key, data);
        return data;
    }

    /** 笔记发生写操作后清缓存，保证仪表盘数据及时更新 */
    public void evict(Long userId) {
        redis.delete(List.of(STATS_KEY + userId, HEATMAP_KEY + userId));
    }

    private long sumWordCount(Long userId) {
        List<Object> values = noteMapper.selectObjs(new QueryWrapper<Note>()
                .select("COALESCE(SUM(CHAR_LENGTH(content_plain)), 0)")
                .eq("user_id", userId)
                .isNull("deleted_at"));
        if (values.isEmpty() || !(values.get(0) instanceof Number number)) {
            return 0;
        }
        return number.longValue();
    }

    /** 按天统计新增笔记数（GROUP BY DATE(created_at)） */
    private List<DayCountVO> dailyCounts(Long userId, java.time.LocalDateTime since) {
        List<Map<String, Object>> rows = noteMapper.selectMaps(new QueryWrapper<Note>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .eq("user_id", userId)
                .isNull("deleted_at")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")
                .orderByAsc("d"));
        List<DayCountVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new DayCountVO(String.valueOf(row.get("d")),
                    ((Number) row.get("c")).longValue()));
        }
        return result;
    }

    private <T> T read(String key, TypeReference<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("统计缓存反序列化失败 key={}", key);
            return null;
        }
    }

    private void write(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写统计缓存失败 key={}", key, e);
        }
    }
}
