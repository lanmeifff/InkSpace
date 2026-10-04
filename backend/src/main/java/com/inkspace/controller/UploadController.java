package com.inkspace.controller;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.api.Result;
import com.inkspace.common.audit.OperationLog;
import com.inkspace.common.exception.BizException;
import com.inkspace.config.AppProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（头像/笔记插图）。
 * 校验：非空、大小上限、content-type、扩展名白名单；文件名随机化，避免路径穿越与覆盖。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final AppProperties appProperties;

    public UploadController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostMapping
    @OperationLog(action = "FILE_UPLOAD", resourceType = "FILE", detail = "#file.originalFilename")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件不能为空");
        }
        if (file.getSize() > appProperties.getUploadMaxBytes()) {
            throw new BizException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(ErrorCode.UPLOAD_TYPE_INVALID);
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.UPLOAD_TYPE_INVALID);
        }

        String subDir = LocalDate.now().format(MONTH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path targetDir = Paths.get(appProperties.getUploadDir(), subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(filename));
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
        return Result.ok("/uploads/" + subDir + "/" + filename);
    }

    private static String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
