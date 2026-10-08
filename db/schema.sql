-- InkSpace 墨记 数据库结构
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 < db/schema.sql
-- 约定：字符集 utf8mb4；时间列交给数据库默认值维护（INSERT 不传即取默认）

CREATE DATABASE IF NOT EXISTS `inkspace`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `inkspace`;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(32)     NOT NULL COMMENT '用户名（唯一，utf8mb4_0900_ai_ci 下不区分大小写）',
    `email`         VARCHAR(64)     NOT NULL COMMENT '邮箱（唯一）',
    `password_hash` VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希，绝不存明文',
    `nickname`      VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar_url`    VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '头像地址',
    `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 正常，0 禁用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

CREATE TABLE IF NOT EXISTS `notebook`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `name`       VARCHAR(32)     NOT NULL COMMENT '笔记本名',
    `icon`       VARCHAR(16)     NOT NULL DEFAULT '' COMMENT '图标（emoji 或短标识）',
    `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '排序值，小的在前',
    `deleted_at` DATETIME        NULL DEFAULT NULL COMMENT '软删除时间，NULL 表示未删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notebook_user` (`user_id`, `deleted_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='笔记本';

CREATE TABLE IF NOT EXISTS `note`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `notebook_id`   BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属笔记本，NULL 表示未分类',
    `title`         VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '标题',
    `content`       LONGTEXT        NULL COMMENT 'Markdown 原文',
    `content_plain` TEXT            NULL COMMENT '去格式纯文本，供全文检索',
    `kind`          VARCHAR(16)     NOT NULL DEFAULT 'manual' COMMENT 'manual/clip/import',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'normal' COMMENT 'draft/normal/archive/inbox',
    `is_favorite`   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否收藏',
    `is_public`     TINYINT         NOT NULL DEFAULT 0 COMMENT '1 表示已公开到博客',
    `published_at`  DATETIME        NULL DEFAULT NULL COMMENT '首次公开到博客的时间，博客列表按它倒序',
    `source_url`    VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '剪藏来源 URL',
    `version`       INT             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted_at`    DATETIME        NULL DEFAULT NULL COMMENT '软删除时间（回收站）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_note_user_updated` (`user_id`, `deleted_at`, `updated_at`),
    KEY `idx_note_user_status` (`user_id`, `status`),
    KEY `idx_note_notebook` (`notebook_id`),
    KEY `idx_note_public` (`is_public`, `published_at`),
    FULLTEXT KEY `ft_note_title_plain` (`title`, `content_plain`) WITH PARSER ngram
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='笔记';

CREATE TABLE IF NOT EXISTS `tag`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '归属用户（标签按用户隔离）',
    `name`       VARCHAR(32)     NOT NULL COMMENT '标签名',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_user_name` (`user_id`, `name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='标签';

CREATE TABLE IF NOT EXISTS `note_tag`
(
    `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id` BIGINT UNSIGNED NOT NULL COMMENT '笔记 id',
    `tag_id`  BIGINT UNSIGNED NOT NULL COMMENT '标签 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_tag` (`note_id`, `tag_id`),
    KEY `idx_note_tag_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='笔记-标签关联';

CREATE TABLE IF NOT EXISTS `share`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id`    BIGINT UNSIGNED NOT NULL COMMENT '被分享笔记',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '分享创建者（用于归属校验）',
    `token`      VARCHAR(64)     NOT NULL COMMENT '公开访问 token（随机 32 字节 hex）',
    `expire_at`  DATETIME        NULL DEFAULT NULL COMMENT '过期时间，NULL 表示永久',
    `view_count` INT             NOT NULL DEFAULT 0 COMMENT '浏览量',
    `closed`     TINYINT         NOT NULL DEFAULT 0 COMMENT '1 已关闭',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_token` (`token`),
    KEY `idx_share_note` (`note_id`),
    KEY `idx_share_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='笔记分享';

CREATE TABLE IF NOT EXISTS `ai_task`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '发起用户',
    `task_type`         VARCHAR(16)     NOT NULL COMMENT 'summary/tag/qa/weekly',
    `status`            VARCHAR(16)     NOT NULL COMMENT 'success/failed',
    `source_id`         BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联笔记 id（可空）',
    `model`             VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '实际使用的模型',
    `prompt_tokens`     INT             NOT NULL DEFAULT 0,
    `completion_tokens` INT             NOT NULL DEFAULT 0,
    `cost`              DECIMAL(10, 6)  NOT NULL DEFAULT 0 COMMENT '预估费用（元）',
    `latency_ms`        INT             NOT NULL DEFAULT 0,
    `error_code`        VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '失败原因',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_task_user` (`user_id`, `created_at`),
    KEY `idx_ai_task_type` (`task_type`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 调用审计';

CREATE TABLE IF NOT EXISTS `audit_log`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '操作人（未登录可空）',
    `action`        VARCHAR(32)     NOT NULL COMMENT '动作，如 NOTE_DELETE',
    `resource_type` VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '资源类型',
    `resource_id`   BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '资源 id',
    `detail`        TEXT            NULL COMMENT '补充信息（JSON）',
    `ip`            VARCHAR(64)     NOT NULL DEFAULT '',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_user` (`user_id`, `created_at`),
    KEY `idx_audit_action` (`action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作审计';

CREATE TABLE IF NOT EXISTS `user_ai_config`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '归属用户，一人一条',
    `provider_name`  VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '服务商名称，用户自取（如 DeepSeek）',
    `url`            VARCHAR(255)    NOT NULL COMMENT 'OpenAI 兼容接口完整地址',
    `api_key_cipher` VARCHAR(512)    NOT NULL DEFAULT '' COMMENT 'API Key：AES-GCM 加密后 base64，绝不存明文',
    `model`          VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '模型名，用户手填',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ai_config_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户自带的 AI 接入配置';
