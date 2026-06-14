package db;-- 创建数据库
CREATE DATABASE IF NOT EXISTS vocabulary_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE vocabulary_app;

-- 1. 用户表
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 单词表
CREATE TABLE `word` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '单词ID',
    `word_text` VARCHAR(100) NOT NULL COMMENT '单词文本',
    `phonetic` VARCHAR(100) COMMENT '音标',
    `part_of_speech` VARCHAR(50) COMMENT '词性',
    `definition` TEXT NOT NULL COMMENT '中文释义',
    `example_sentence` TEXT COMMENT '例句',
    `example_translation` TEXT COMMENT '例句翻译',
    `audio_url` VARCHAR(255) COMMENT '发音音频URL',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_word (`word_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- 3. 单词书表
CREATE TABLE `vocabulary_book` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '单词书ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `book_name` VARCHAR(100) NOT NULL COMMENT '单词书名称',
    `description` TEXT COMMENT '单词书描述',
    `cover_image` VARCHAR(255) COMMENT '封面图片URL',
    `word_count` INT DEFAULT 0 COMMENT '单词数量',
    `is_public` TINYINT DEFAULT 0 COMMENT '是否公开：0-私有，1-公开',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    INDEX idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词书表';

-- 4. 单词书-单词关联表（记录哪个用户的哪本单词书包含哪些单词）
CREATE TABLE `book_word` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `book_id` BIGINT NOT NULL COMMENT '单词书ID',
    `word_id` BIGINT NOT NULL COMMENT '单词ID',
    `mastered` TINYINT DEFAULT 0 COMMENT '是否掌握：0-未掌握，1-已掌握',
    `note` TEXT COMMENT '笔记',
    `review_count` INT DEFAULT 0 COMMENT '复习次数',
    `last_review_time` DATETIME COMMENT '最后复习时间',
    `difficulty` TINYINT DEFAULT 2 COMMENT '难度等级：1-简单，2-中等，3-困难',
    `priority` INT DEFAULT 0 COMMENT '学习优先级',
    `tags` VARCHAR(255) COMMENT '标签，逗号分隔',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`book_id`) REFERENCES `vocabulary_book`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`word_id`) REFERENCES `word`(`id`) ON DELETE CASCADE,
    UNIQUE KEY uk_user_book_word (`user_id`, `book_id`, `word_id`),
    INDEX idx_user (`user_id`),
    INDEX idx_book (`book_id`),
    INDEX idx_word (`word_id`),
    INDEX idx_mastered (`mastered`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词书-单词关联表';

-- 插入测试数据
INSERT INTO `word` (`word_text`, `phonetic`, `part_of_speech`, `definition`, `example_sentence`, `example_translation`) VALUES
('apple', '/ˈæpl/', 'n.', '苹果', 'I eat an apple every day.', '我每天吃一个苹果。'),
('book', '/bʊk/', 'n.', '书；书籍', 'This is a good book.', '这是一本好书。'),
('computer', '/kəmˈpjuːtər/', 'n.', '计算机；电脑', 'I use a computer for work.', '我用电脑工作。'),
('hello', '/həˈloʊ/', 'int.', '你好', 'Hello, how are you?', '你好，你好吗？'),
('world', '/wɜːrld/', 'n.', '世界', 'The world is beautiful.', '这个世界很美丽。');
