package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AppProperties;
import com.inkspace.domain.entity.Note;
import com.inkspace.domain.entity.Share;
import com.inkspace.dto.ShareCreateRequest;
import com.inkspace.mapper.NoteMapper;
import com.inkspace.mapper.ShareMapper;
import com.inkspace.vo.PublicNoteVO;
import com.inkspace.vo.ShareVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 分享：生成/关闭链接 + 公开只读访问。
 *
 * Redis 场景：公开页热读缓存 share:{token}（只缓存笔记内容，不缓存浏览量，保证计数准），
 * 关闭分享时主动删缓存。
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final String CACHE_PREFIX = "share:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_EXPIRE_HOURS = 24 * 7;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ShareMapper shareMapper;
    private final NoteMapper noteMapper;
    private final NoteService noteService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public ShareService(ShareMapper shareMapper,
                        NoteMapper noteMapper,
                        NoteService noteService,
                        StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        AppProperties appProperties) {
        this.shareMapper = shareMapper;
        this.noteMapper = noteMapper;
        this.noteService = noteService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public ShareVO create(Long userId, ShareCreateRequest request) {
        noteService.requireOwned(userId, request.getNoteId());

        int hours = request.getExpireHours() == null ? DEFAULT_EXPIRE_HOURS : request.getExpireHours();
        Share share = new Share();
        share.setNoteId(request.getNoteId());
        share.setUserId(userId);
        share.setToken(randomToken());
        share.setExpireAt(LocalDateTime.now().plusHours(hours));
        share.setViewCount(0);
        share.setClosed(false);
        shareMapper.insert(share);

        log.info("创建分享 shareId={}, noteId={}, expireHours={}", share.getId(), share.getNoteId(), hours);
        return ShareVO.from(shareMapper.selectById(share.getId()), appProperties.getShareBaseUrl());
    }

    public void close(Long userId, Long shareId) {
        Share share = shareMapper.selectOne(new LambdaQueryWrapper<Share>()
                .eq(Share::getId, shareId)
                .eq(Share::getUserId, userId));
        if (share == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(share.getClosed())) {
            Share update = new Share();
            update.setClosed(true);
            shareMapper.update(update, new LambdaQueryWrapper<Share>().eq(Share::getId, shareId));
            redis.delete(CACHE_PREFIX + share.getToken());
        }
    }

    /** 公开访问：仅凭 token，无需登录 */
    public PublicNoteVO publicGet(String token) {
        Share share = shareMapper.selectOne(new LambdaQueryWrapper<Share>()
                .eq(Share::getToken, token)
                .eq(Share::getClosed, false));
        if (share == null || (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now()))) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        PublicNoteVO cached = readCache(token);
        if (cached == null) {
            Note note = noteMapper.selectById(share.getNoteId());
            if (note == null || note.getDeletedAt() != null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            cached = new PublicNoteVO(note.getTitle(), note.getContent(), note.getUpdatedAt(), 0);
            writeCache(token, cached);
        }

        // 浏览量每次真实落库（缓存只负责正文内容），返回最新值
        shareMapper.update(null, new LambdaUpdateWrapper<Share>()
                .eq(Share::getId, share.getId())
                .setSql("view_count = view_count + 1"));
        int views = (share.getViewCount() == null ? 0 : share.getViewCount()) + 1;
        cached.setViewCount(views);
        return cached;
    }

    private PublicNoteVO readCache(String token) {
        String json = redis.opsForValue().get(CACHE_PREFIX + token);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PublicNoteVO.class);
        } catch (Exception e) {
            log.warn("分享缓存反序列化失败，回源查库 token={}", token);
            return null;
        }
    }

    private void writeCache(String token, PublicNoteVO vo) {
        try {
            redis.opsForValue().set(CACHE_PREFIX + token, objectMapper.writeValueAsString(vo), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写分享缓存失败 token={}", token, e);
        }
    }

    private static String randomToken() {
        byte[] buffer = new byte[32];
        RANDOM.nextBytes(buffer);
        return HEX.formatHex(buffer);
    }
}
