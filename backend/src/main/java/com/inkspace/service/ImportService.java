package com.inkspace.service;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.dto.NoteCreateRequest;
import com.inkspace.vo.NoteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 批量导入。
 * 用 Redis SETNX 锁 lock:import:{userId} 防止重复提交导致同一批文件被导入多次。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final String LOCK_KEY = "lock:import:";
    private static final int MAX_FILES = 20;
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    private final NoteService noteService;
    private final StringRedisTemplate redis;

    public ImportService(NoteService noteService, StringRedisTemplate redis) {
        this.noteService = noteService;
        this.redis = redis;
    }

    public int importMarkdown(Long userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要导入的文件");
        }
        if (files.size() > MAX_FILES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次最多导入 " + MAX_FILES + " 个文件");
        }

        String lockKey = LOCK_KEY + userId;
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已有导入任务进行中，请稍后再试");
        }
        try {
            List<NoteVO> imported = new ArrayList<>();
            for (MultipartFile file : files) {
                imported.add(importOne(userId, file));
            }
            log.info("批量导入完成 userId={}, count={}", userId, imported.size());
            return imported.size();
        } finally {
            redis.delete(lockKey);
        }
    }

    private NoteVO importOne(Long userId, MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件过大：" + file.getOriginalFilename());
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件读取失败：" + file.getOriginalFilename());
        }

        NoteCreateRequest create = new NoteCreateRequest();
        create.setTitle(resolveTitle(file.getOriginalFilename(), content));
        create.setContent(content);
        create.setKind("import");
        create.setStatus("normal");
        return noteService.create(userId, create);
    }

    /** 标题优先取正文第一个一级标题，否则用文件名 */
    private String resolveTitle(String filename, String content) {
        for (String line : content.split("\n", 20)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).trim();
                if (StringUtils.hasText(title)) {
                    return title.length() > 200 ? title.substring(0, 200) : title;
                }
            }
        }
        String name = StringUtils.hasText(filename) ? filename : "未命名";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
