/*
 Navicat Premium Dump SQL

 Source Server         : my_computer
 Source Server Type    : MySQL
 Source Server Version : 80037 (8.0.37)
 Source Host           : localhost:3306
 Source Schema         : app

 Target Server Type    : MySQL
 Target Server Version : 80037 (8.0.37)
 File Encoding         : 65001

 Date: 19/05/2026 17:30:33
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for public_book_word
-- ----------------------------
DROP TABLE IF EXISTS `public_book_word`;
CREATE TABLE `public_book_word`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `book_id` bigint NOT NULL COMMENT '公共单词书ID',
  `word_id` bigint NOT NULL COMMENT '公共单词ID',
  `sort_order` int NULL DEFAULT 0 COMMENT '单词在书中的排序',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_book_word`(`book_id` ASC, `word_id` ASC) USING BTREE,
  INDEX `idx_book`(`book_id` ASC) USING BTREE,
  INDEX `idx_word`(`word_id` ASC) USING BTREE,
  INDEX `idx_sort`(`sort_order` ASC) USING BTREE,
  CONSTRAINT `public_book_word_ibfk_1` FOREIGN KEY (`book_id`) REFERENCES `public_vocabulary_book` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `public_book_word_ibfk_2` FOREIGN KEY (`word_id`) REFERENCES `public_word` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2493 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公共单词书-单词关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for public_vocabulary_book
-- ----------------------------
DROP TABLE IF EXISTS `public_vocabulary_book`;
CREATE TABLE `public_vocabulary_book`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词书ID',
  `book_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单词书名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '单词书描述',
  `cover_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面图片URL',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类：cet4-四级，cet6-六级，ielts-雅思，toefl-托福，business-商务，daily-日常',
  `difficulty` tinyint NULL DEFAULT 1 COMMENT '难度等级：1-初级，2-中级，3-高级',
  `word_count` int NULL DEFAULT 0 COMMENT '单词数量',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  INDEX `idx_difficulty`(`difficulty` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公共单词书表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for public_word
-- ----------------------------
DROP TABLE IF EXISTS `public_word`;
CREATE TABLE `public_word`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公共单词ID',
  `word_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单词文本',
  `phonetic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '音标',
  `part_of_speech` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '词性',
  `definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中文释义',
  `example_sentence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '例句',
  `example_translation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '例句翻译',
  `audio_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发音音频URL',
  `difficulty_level` tinyint NULL DEFAULT 1 COMMENT '难度等级：1-简单，2-中等，3-困难',
  `frequency_rank` int NULL DEFAULT NULL COMMENT '词频排名',
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签，逗号分隔',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_word_text`(`word_text` ASC) USING BTREE,
  INDEX `idx_difficulty`(`difficulty_level` ASC) USING BTREE,
  INDEX `idx_frequency`(`frequency_rank` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2114 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公共单词表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for store_product
-- ----------------------------
DROP TABLE IF EXISTS `store_product`;
CREATE TABLE `store_product`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `product_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `product_type` tinyint NOT NULL DEFAULT 1 COMMENT '商品类型：1-单词书',
  `reference_id` bigint NOT NULL COMMENT '关联ID（如公共单词书ID）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商品描述',
  `cover_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面图片URL',
  `price` int NOT NULL DEFAULT 0 COMMENT '价格（积分）',
  `original_price` int NULL DEFAULT NULL COMMENT '原价（积分），用于显示折扣',
  `is_hot` tinyint NULL DEFAULT 0 COMMENT '是否热门：0-否，1-是',
  `is_new` tinyint NULL DEFAULT 0 COMMENT '是否新品：0-否，1-是',
  `is_recommended` tinyint NULL DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序权重（越大越靠前）',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `stock` int NULL DEFAULT -1 COMMENT '库存（-1表示无限）',
  `sales_count` int NULL DEFAULT 0 COMMENT '销售数量',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`product_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_sort`(`sort_order` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商店商品表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for store_purchase_record
-- ----------------------------
DROP TABLE IF EXISTS `store_purchase_record`;
CREATE TABLE `store_purchase_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购买记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `price_paid` int NOT NULL COMMENT '购买时支付的价格',
  `purchase_type` tinyint NULL DEFAULT 1 COMMENT '购买类型：1-正常购买，2-免费领取，3-VIP赠送',
  `user_book_id` bigint NULL DEFAULT NULL COMMENT '购买后生成的用户单词书ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE,
  INDEX `idx_created`(`created_at` ASC) USING BTREE,
  INDEX `store_purchase_record_ibfk_3`(`user_book_id` ASC) USING BTREE,
  CONSTRAINT `store_purchase_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `store_purchase_record_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `store_product` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `store_purchase_record_ibfk_3` FOREIGN KEY (`user_book_id`) REFERENCES `user_vocabulary_book` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商店购买记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码（加密存储）',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像URL',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `idx_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_book_word
-- ----------------------------
DROP TABLE IF EXISTS `user_book_word`;
CREATE TABLE `user_book_word`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `book_id` bigint NOT NULL COMMENT '用户单词书ID',
  `word_id` bigint NOT NULL COMMENT '用户单词ID',
  `mastered` tinyint NULL DEFAULT 0 COMMENT '是否掌握：0-未掌握，1-已掌握',
  `review_count` int NULL DEFAULT 0 COMMENT '复习次数',
  `last_review_time` datetime NULL DEFAULT NULL COMMENT '最后复习时间',
  `difficulty` tinyint NULL DEFAULT 2 COMMENT '难度等级：1-简单，2-中等，3-困难',
  `priority` int NULL DEFAULT 0 COMMENT '学习优先级',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_book_word`(`user_id` ASC, `book_id` ASC, `word_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_book`(`book_id` ASC) USING BTREE,
  INDEX `idx_word`(`word_id` ASC) USING BTREE,
  INDEX `idx_mastered`(`mastered` ASC) USING BTREE,
  CONSTRAINT `user_book_word_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `user_book_word_ibfk_2` FOREIGN KEY (`book_id`) REFERENCES `user_vocabulary_book` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `user_book_word_ibfk_3` FOREIGN KEY (`word_id`) REFERENCES `user_word` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2423 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户单词书-单词关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_checkin
-- ----------------------------
DROP TABLE IF EXISTS `user_checkin`;
CREATE TABLE `user_checkin`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '签到ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `checkin_date` date NOT NULL COMMENT '签到日期',
  `continuous_days` int NULL DEFAULT 1 COMMENT '连续签到天数',
  `points_earned` int NULL DEFAULT 10 COMMENT '获得的积分',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id` ASC, `checkin_date` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_date`(`checkin_date` ASC) USING BTREE,
  CONSTRAINT `user_checkin_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户签到记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_points_account
-- ----------------------------
DROP TABLE IF EXISTS `user_points_account`;
CREATE TABLE `user_points_account`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` int NULL DEFAULT 0 COMMENT '当前积分余额',
  `total_earned` int NULL DEFAULT 0 COMMENT '累计获得积分',
  `total_spent` int NULL DEFAULT 0 COMMENT '累计消费积分',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_balance`(`balance` ASC) USING BTREE,
  CONSTRAINT `user_points_account_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户积分账户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_points_transaction
-- ----------------------------
DROP TABLE IF EXISTS `user_points_transaction`;
CREATE TABLE `user_points_transaction`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交易ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `type` tinyint NOT NULL COMMENT '交易类型：1-注册赠送，2-每日签到，3-学习奖励，4-购买消费，5-系统调整',
  `amount` int NOT NULL COMMENT '积分变化量（正数为增加，负数为减少）',
  `balance_after` int NOT NULL COMMENT '交易后余额',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '交易描述',
  `reference_id` bigint NULL DEFAULT NULL COMMENT '关联ID（如购买的单词书ID）',
  `idempotency_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '幂等性标识',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_idempotency_key`(`idempotency_key` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_created`(`created_at` ASC) USING BTREE,
  CONSTRAINT `user_points_transaction_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 59 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户积分交易记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_vocabulary_book
-- ----------------------------
DROP TABLE IF EXISTS `user_vocabulary_book`;
CREATE TABLE `user_vocabulary_book`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词书ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `book_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单词书名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '单词书描述',
  `cover_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面图片URL',
  `word_count` int NULL DEFAULT 0 COMMENT '单词数量',
  `is_public` tinyint NULL DEFAULT 0 COMMENT '是否公开：0-私有，1-公开',
  `source_type` tinyint NULL DEFAULT 1 COMMENT '来源类型：1-手动创建，2-从商店购买',
  `source_store_book_id` bigint NULL DEFAULT NULL COMMENT '来源商店单词书ID（如果从商店购买）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  CONSTRAINT `user_vocabulary_book_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 34 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户单词书表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_word
-- ----------------------------
DROP TABLE IF EXISTS `user_word`;
CREATE TABLE `user_word`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `word_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '单词文本',
  `phonetic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '音标',
  `part_of_speech` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '词性',
  `definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中文释义',
  `example_sentence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '例句',
  `example_translation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '例句翻译',
  `audio_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发音音频URL',
  `note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '个人笔记',
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签，逗号分隔',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_word_text`(`word_text` ASC) USING BTREE,
  CONSTRAINT `user_word_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1579 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户单词表（生词本）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Triggers structure for table public_book_word
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_update_public_book_word_count_insert`;
delimiter ;;
CREATE TRIGGER `trg_update_public_book_word_count_insert` AFTER INSERT ON `public_book_word` FOR EACH ROW BEGIN
    UPDATE `public_vocabulary_book` 
    SET `word_count` = (SELECT COUNT(*) FROM `public_book_word` WHERE `book_id` = NEW.`book_id`)
    WHERE `id` = NEW.`book_id`;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table public_book_word
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_update_public_book_word_count_delete`;
delimiter ;;
CREATE TRIGGER `trg_update_public_book_word_count_delete` AFTER DELETE ON `public_book_word` FOR EACH ROW BEGIN
    UPDATE `public_vocabulary_book` 
    SET `word_count` = (SELECT COUNT(*) FROM `public_book_word` WHERE `book_id` = OLD.`book_id`)
    WHERE `id` = OLD.`book_id`;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table user_book_word
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_update_user_book_word_count_insert`;
delimiter ;;
CREATE TRIGGER `trg_update_user_book_word_count_insert` AFTER INSERT ON `user_book_word` FOR EACH ROW BEGIN
    UPDATE `user_vocabulary_book` 
    SET `word_count` = (SELECT COUNT(*) FROM `user_book_word` WHERE `book_id` = NEW.`book_id`)
    WHERE `id` = NEW.`book_id`;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table user_book_word
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_update_user_book_word_count_delete`;
delimiter ;;
CREATE TRIGGER `trg_update_user_book_word_count_delete` AFTER DELETE ON `user_book_word` FOR EACH ROW BEGIN
    UPDATE `user_vocabulary_book` 
    SET `word_count` = (SELECT COUNT(*) FROM `user_book_word` WHERE `book_id` = OLD.`book_id`)
    WHERE `id` = OLD.`book_id`;
END
;;
delimiter ;

-- ----------------------------
-- Table structure for seckill_activity
-- ----------------------------
DROP TABLE IF EXISTS `seckill_activity`;
CREATE TABLE `seckill_activity`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '秒杀活动ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `seckill_price` int NOT NULL COMMENT '秒杀价格（积分）',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存数量',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_time`(`start_time` ASC, `end_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀活动表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for seckill_order
-- ----------------------------
DROP TABLE IF EXISTS `seckill_order`;
CREATE TABLE `seckill_order`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '秒杀活动ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态：0-处理中，1-已完成，2-异常',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_activity`(`user_id` ASC, `activity_id` ASC) USING BTREE COMMENT '防止重复购买',
  UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for seckill_message_log
-- ----------------------------
DROP TABLE IF EXISTS `seckill_message_log`;
CREATE TABLE `seckill_message_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息日志ID',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息ID',
  `message_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息内容',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态：0-发送中，1-成功，2-失败',
  `retry_count` int NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_id`(`message_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀消息日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for slow_query_log
-- ----------------------------
DROP TABLE IF EXISTS `slow_query_log`;
CREATE TABLE `slow_query_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `method_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '方法全名（类名.方法名）',
  `cost_ms` bigint NOT NULL COMMENT '耗时（毫秒）',
  `threshold_ms` bigint NOT NULL DEFAULT 500 COMMENT '触发阈值（毫秒）',
  `result_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '返回类型',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_cost`(`cost_ms` ASC) USING BTREE,
  INDEX `idx_created`(`created_at` ASC) USING BTREE,
  INDEX `idx_method`(`method_name`(100) ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '慢查询日志表（AOP自动记录）' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
