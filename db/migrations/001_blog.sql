-- 迁移 001：博客公开字段
-- 用法（已有库执行一次即可，幂等）：
--   mysql -uroot -p --default-character-set=utf8mb4 < db/migrations/001_blog.sql
--
-- 说明：schema.sql 里的 CREATE TABLE IF NOT EXISTS 只对全新库生效，
-- 已经建过表的库要靠这个脚本补列与索引。

USE `inkspace`;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `note` ADD COLUMN `is_public` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 表示已公开到博客'' AFTER `is_favorite`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'inkspace' AND TABLE_NAME = 'note' AND COLUMN_NAME = 'is_public'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `note` ADD COLUMN `published_at` DATETIME NULL DEFAULT NULL COMMENT ''首次公开到博客的时间'' AFTER `is_public`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'inkspace' AND TABLE_NAME = 'note' AND COLUMN_NAME = 'published_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `note` ADD KEY `idx_note_public` (`is_public`, `published_at`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'inkspace' AND TABLE_NAME = 'note' AND INDEX_NAME = 'idx_note_public'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
