package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkspace.common.api.PageResult;
import com.inkspace.domain.entity.Note;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.vo.BlogPostDetailVO;
import com.inkspace.vo.BlogPostVO;
import com.inkspace.vo.NoteVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 博客公开逻辑自检：发布开关、列表只含公开笔记、改内容不掉线、published_at 语义。
 *
 * 断言一律用"相对基线"的差值，所以本地库里已经有博客文章也不会互相干扰。
 * 需要本地 MySQL(3306) 与 Redis(6379)；没起依赖时整个类跳过，
 * 避免别人 clone 下来 mvn test 直接变红。
 */
@SpringBootTest
class BlogServiceTest {

    private static final Long USER_ID = 999_000_001L;

    @Autowired
    private BlogService blogService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteMapper noteMapper;

    @BeforeAll
    static void requireDependencies() {
        Assumptions.assumeTrue(reachable(3306), "本地 MySQL 未启动，跳过博客集成自检");
        Assumptions.assumeTrue(reachable(6379), "本地 Redis 未启动，跳过博客集成自检");
    }

    @AfterEach
    void cleanup() {
        noteMapper.delete(new LambdaQueryWrapper<Note>().eq(Note::getUserId, USER_ID));
    }

    @Test
    void publishToggleControlsBlogVisibility() {
        int basePosts = publishedCount();
        Note note = seed("线程池的七个参数", "核心线程数、最大线程数、队列与拒绝策略，决定了任务的排队与执行行为。");

        assertEquals(basePosts, publishedCount(), "未公开的笔记不该出现在博客里");

        NoteVO published = noteService.setPublished(USER_ID, note.getId(), true);
        assertTrue(published.getIsPublic());
        assertNotNull(published.getPublishedAt(), "首次公开必须写入 published_at");

        assertEquals(basePosts + 1, publishedCount());
        BlogPostVO post = findPublished(note.getId());
        assertNotNull(post, "公开后应出现在博客列表里");
        assertEquals("线程池的七个参数", post.title());
        assertTrue(post.excerpt().contains("核心线程数"), "摘要应来自正文纯文本");
        assertEquals(1, post.readTime());

        BlogPostDetailVO detail = blogService.detail(note.getId());
        assertTrue(detail.content().contains("拒绝策略"));

        // 侧栏统计要把它算进去。
        // 这里刻意断言 totalPosts 与 tagCount，而不是"热门列表里有没有这篇"：
        // 热门只取前 5 篇且按"收藏优先 → 正文更长"排序，本地库里有别人/别的长文时，
        // 这种断言会随库内容变化而失败（踩过一次）。totalPosts 与 tagCount 是确定性的。
        Map<String, Object> sidebar = blogService.sidebar();
        assertEquals(basePosts + 1, sidebar.get("totalPosts"));
        assertTrue(((Number) sidebar.get("tagCount")).intValue() >= 1, "带标签的公开文章应计入 tagCount");

        LocalDateTime firstPublishedAt = published.getPublishedAt();
        noteService.setPublished(USER_ID, note.getId(), false);
        assertEquals(basePosts, publishedCount(), "取消公开后应从列表消失");

        NoteVO republished = noteService.setPublished(USER_ID, note.getId(), true);
        assertEquals(firstPublishedAt, republished.getPublishedAt(), "重新公开应保留最早的公开时间");
    }

    @Test
    void editingPublishedNoteKeepsItVisible() {
        int basePosts = publishedCount();
        Note note = seed("缓存击穿复盘", "热点 key 过期瞬间打满数据库，加互斥重建与逻辑过期两层兜底。");
        noteService.setPublished(USER_ID, note.getId(), true);

        Note edit = new Note();
        edit.setTitle("缓存击穿复盘（修订）");
        edit.setContentPlain("补一段：互斥重建要配合超时，否则线程会堆在锁上。");
        edit.setVersion(note.getVersion() + 1);
        noteMapper.update(edit, new LambdaQueryWrapper<Note>()
                .eq(Note::getId, note.getId())
                .eq(Note::getUserId, USER_ID));

        assertEquals(basePosts + 1, publishedCount(), "改内容不该把文章从博客摘掉");
        BlogPostDetailVO afterEdit = blogService.detail(note.getId());
        assertEquals("缓存击穿复盘（修订）", afterEdit.title());
        assertTrue(afterEdit.content().contains("互斥重建"));
    }

    private int publishedCount() {
        return (int) blogService.list(1, 1, null).getTotal();
    }

    /** 只在当前页里找；结合 publishedCount 使用，确保不会因为别人的数据而误判 */
    private BlogPostVO findPublished(Long noteId) {
        PageResult<BlogPostVO> page = blogService.list(1, 50, null);
        return page.getList().stream()
                .filter(post -> post.id().equals(noteId))
                .findFirst()
                .orElse(null);
    }

    private static boolean reachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Note seed(String title, String plain) {
        Note note = new Note();
        note.setUserId(USER_ID);
        note.setTitle(title);
        note.setContent(plain);
        note.setContentPlain(plain);
        note.setKind("manual");
        note.setStatus("normal");
        note.setIsFavorite(false);
        note.setIsPublic(false);
        note.setSourceUrl("");
        note.setVersion(1);
        noteMapper.insert(note);
        return note;
    }
}
