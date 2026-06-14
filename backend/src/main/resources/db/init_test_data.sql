/*
 * 数据库测试数据初始化脚本
 * 为所有表添加基础测试数据
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- 1. 用户表 (user) - 添加5个测试用户
-- ========================================
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`) VALUES
(1, 'testuser1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户1', 'https://example.com/avatar1.jpg'),
(2, 'testuser2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户2', 'https://example.com/avatar2.jpg'),
(3, 'testuser3', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '学习达人', 'https://example.com/avatar3.jpg'),
(4, 'testuser4', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '英语小白', 'https://example.com/avatar4.jpg'),
(5, 'testuser5', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '单词大师', 'https://example.com/avatar5.jpg');

-- ========================================
-- 2. 公共单词表 (public_word) - 添加20个常用单词
-- ========================================
INSERT INTO `public_word` (`id`, `word_text`, `phonetic`, `part_of_speech`, `definition`, `example_sentence`, `example_translation`, `difficulty_level`, `frequency_rank`, `tags`) VALUES
(1, 'apple', '/ˈæpl/', 'n.', '苹果', 'I eat an apple every day.', '我每天吃一个苹果。', 1, 100, 'fruit,daily'),
(2, 'book', '/bʊk/', 'n.', '书；书籍', 'She is reading a book.', '她正在读一本书。', 1, 50, 'education,daily'),
(3, 'computer', '/kəmˈpjuːtər/', 'n.', '计算机；电脑', 'I work on my computer.', '我在电脑上工作。', 1, 200, 'technology'),
(4, 'happy', '/ˈhæpi/', 'adj.', '快乐的；幸福的', 'She feels very happy today.', '她今天感觉很快乐。', 1, 80, 'emotion'),
(5, 'learn', '/lɜːrn/', 'v.', '学习；学会', 'I want to learn English.', '我想学习英语。', 1, 60, 'education'),
(6, 'music', '/ˈmjuːzɪk/', 'n.', '音乐', 'I love listening to music.', '我喜欢听音乐。', 1, 150, 'art,daily'),
(7, 'friend', '/frend/', 'n.', '朋友', 'He is my best friend.', '他是我最好的朋友。', 1, 70, 'social'),
(8, 'water', '/ˈwɔːtər/', 'n.', '水', 'Please give me some water.', '请给我一些水。', 1, 40, 'daily'),
(9, 'time', '/taɪm/', 'n.', '时间', 'What time is it?', '现在几点了？', 1, 10, 'daily'),
(10, 'work', '/wɜːrk/', 'v.', '工作', 'I work in an office.', '我在办公室工作。', 1, 30, 'business'),
(11, 'beautiful', '/ˈbjuːtɪfl/', 'adj.', '美丽的', 'The flower is beautiful.', '这朵花很美丽。', 2, 300, 'description'),
(12, 'important', '/ɪmˈpɔːrtnt/', 'adj.', '重要的', 'This is very important.', '这非常重要。', 2, 120, 'description'),
(13, 'knowledge', '/ˈnɑːlɪdʒ/', 'n.', '知识', 'Knowledge is power.', '知识就是力量。', 2, 250, 'education'),
(14, 'experience', '/ɪkˈspɪriəns/', 'n.', '经验；经历', 'She has much experience.', '她有很多经验。', 2, 180, 'business'),
(15, 'challenge', '/ˈtʃælɪndʒ/', 'n.', '挑战', 'Life is full of challenges.', '生活充满挑战。', 2, 400, 'abstract'),
(16, 'opportunity', '/ˌɑːpərˈtuːnəti/', 'n.', '机会', 'This is a great opportunity.', '这是一个很好的机会。', 3, 500, 'business'),
(17, 'responsibility', '/rɪˌspɑːnsəˈbɪləti/', 'n.', '责任', 'We must take responsibility.', '我们必须承担责任。', 3, 600, 'abstract'),
(18, 'philosophy', '/fəˈlɑːsəfi/', 'n.', '哲学', 'He studies philosophy.', '他学习哲学。', 3, 800, 'academic'),
(19, 'phenomenon', '/fəˈnɑːmɪnɑːn/', 'n.', '现象', 'This is a natural phenomenon.', '这是一种自然现象。', 3, 900, 'academic'),
(20, 'sophisticated', '/səˈfɪstɪkeɪtɪd/', 'adj.', '复杂的；精密的', 'This is a sophisticated system.', '这是一个复杂的系统。', 3, 1000, 'advanced');

-- ========================================
-- 3. 公共单词书表 (public_vocabulary_book) - 添加5本单词书
-- ========================================
INSERT INTO `public_vocabulary_book` (`id`, `book_name`, `description`, `cover_image`, `category`, `difficulty`, `word_count`) VALUES
(1, 'CET-4核心词汇', '大学英语四级考试核心词汇精选', 'https://example.com/cet4.jpg', 'cet4', 2, 0),
(2, 'CET-6高频词汇', '大学英语六级考试高频词汇', 'https://example.com/cet6.jpg', 'cet6', 3, 0),
(3, '雅思基础词汇', '雅思考试基础必备词汇', 'https://example.com/ielts.jpg', 'ielts', 3, 0),
(4, '商务英语入门', '商务场景常用英语词汇', 'https://example.com/business.jpg', 'business', 2, 0),
(5, '日常口语词汇', '日常生活常用英语口语词汇', 'https://example.com/daily.jpg', 'daily', 1, 0);

-- ========================================
-- 4. 公共单词书-单词关联表 (public_book_word) - 为每本书添加单词
-- ========================================
-- CET-4核心词汇 (book_id=1) - 添加10个单词
INSERT INTO `public_book_word` (`book_id`, `word_id`, `sort_order`) VALUES
(1, 1, 1), (1, 2, 2), (1, 3, 3), (1, 4, 4), (1, 5, 5),
(1, 6, 6), (1, 7, 7), (1, 8, 8), (1, 9, 9), (1, 10, 10);

-- CET-6高频词汇 (book_id=2) - 添加8个单词
INSERT INTO `public_book_word` (`book_id`, `word_id`, `sort_order`) VALUES
(2, 11, 1), (2, 12, 2), (2, 13, 3), (2, 14, 4),
(2, 15, 5), (2, 16, 6), (2, 17, 7), (2, 18, 8);

-- 雅思基础词汇 (book_id=3) - 添加6个单词
INSERT INTO `public_book_word` (`book_id`, `word_id`, `sort_order`) VALUES
(3, 16, 1), (3, 17, 2), (3, 18, 3), (3, 19, 4), (3, 20, 5);

-- 商务英语入门 (book_id=4) - 添加5个单词
INSERT INTO `public_book_word` (`book_id`, `word_id`, `sort_order`) VALUES
(4, 10, 1), (4, 12, 2), (4, 14, 3), (4, 16, 4), (4, 17, 5);

-- 日常口语词汇 (book_id=5) - 添加8个单词
INSERT INTO `public_book_word` (`book_id`, `word_id`, `sort_order`) VALUES
(5, 1, 1), (5, 4, 2), (5, 6, 3), (5, 7, 4),
(5, 8, 5), (5, 9, 6), (5, 2, 7), (5, 3, 8);

-- ========================================
-- 5. 商店商品表 (store_product) - 添加5个商品
-- ========================================
INSERT INTO `store_product` (`id`, `product_name`, `product_type`, `reference_id`, `description`, `cover_image`, `price`, `original_price`, `is_hot`, `is_new`, `is_recommended`, `sort_order`, `status`, `stock`, `sales_count`) VALUES
(1, 'CET-4核心词汇', 1, 1, '大学英语四级考试核心词汇精选，包含高频考点', 'https://example.com/cet4.jpg', 500, 800, 1, 0, 1, 100, 1, -1, 50),
(2, 'CET-6高频词汇', 1, 2, '大学英语六级考试高频词汇，助你顺利通过六级', 'https://example.com/cet6.jpg', 600, 1000, 1, 0, 1, 90, 1, -1, 30),
(3, '雅思基础词汇', 1, 3, '雅思考试基础必备词汇，留学必备', 'https://example.com/ielts.jpg', 800, 1200, 0, 1, 1, 80, 1, -1, 20),
(4, '商务英语入门', 1, 4, '商务场景常用英语词汇，职场必备', 'https://example.com/business.jpg', 400, 600, 0, 0, 0, 70, 1, -1, 15),
(5, '日常口语词汇', 1, 5, '日常生活常用英语口语词汇，轻松交流', 'https://example.com/daily.jpg', 300, 500, 1, 1, 0, 60, 1, -1, 40);

-- ========================================
-- 6. 用户积分账户表 (user_points_account) - 为每个用户创建积分账户
-- ========================================
INSERT INTO `user_points_account` (`user_id`, `balance`, `total_earned`, `total_spent`) VALUES
(1, 5000, 5000, 0),
(2, 3000, 3000, 0),
(3, 8000, 8000, 0),
(4, 1000, 1000, 0),
(5, 10000, 10000, 0);

-- ========================================
-- 7. 用户积分交易记录表 (user_points_transaction) - 添加积分交易记录
-- ========================================
INSERT INTO `user_points_transaction` (`user_id`, `type`, `amount`, `balance_after`, `description`) VALUES
(1, 1, 5000, 5000, '注册赠送积分'),
(2, 1, 3000, 3000, '注册赠送积分'),
(3, 1, 5000, 5000, '注册赠送积分'),
(3, 2, 3000, 8000, '连续签到奖励'),
(4, 1, 1000, 1000, '注册赠送积分'),
(5, 1, 5000, 5000, '注册赠送积分'),
(5, 3, 5000, 10000, '学习成就奖励');

-- ========================================
-- 8. 用户签到记录表 (user_checkin) - 添加签到记录
-- ========================================
INSERT INTO `user_checkin` (`user_id`, `checkin_date`, `continuous_days`, `points_earned`) VALUES
(1, CURDATE(), 1, 10),
(2, CURDATE(), 1, 10),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2, 10),
(3, CURDATE(), 5, 50),
(3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 4, 40),
(3, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 3, 30),
(3, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2, 20),
(3, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1, 10),
(4, CURDATE(), 1, 10),
(5, CURDATE(), 10, 100),
(5, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 9, 90),
(5, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 8, 80);

-- ========================================
-- 9. 用户单词书表 (user_vocabulary_book) - 为用户创建单词书
-- ========================================
INSERT INTO `user_vocabulary_book` (`user_id`, `book_name`, `description`, `cover_image`, `word_count`, `is_public`, `source_type`) VALUES
(1, '我的生词本', '个人收集的生词', NULL, 0, 0, 1),
(1, 'CET-4学习计划', '从商店购买的CET-4词汇', 'https://example.com/cet4.jpg', 0, 0, 2),
(2, '英语学习笔记', '日常学习积累的单词', NULL, 0, 0, 1),
(3, '雅思备考词汇', '准备雅思考试的词汇', 'https://example.com/ielts.jpg', 0, 0, 2),
(3, '商务英语积累', '工作中遇到的商务词汇', NULL, 0, 1),
(4, '基础词汇入门', '初学者基础词汇', NULL, 0, 0, 1),
(5, '高级词汇精选', '高级英语词汇收藏', NULL, 0, 0, 1),
(5, '每日新词', '每天学习的新单词', NULL, 0, 0, 1);

-- ========================================
-- 10. 用户单词表 (user_word) - 为用户添加个人单词
-- ========================================
INSERT INTO `user_word` (`user_id`, `word_text`, `phonetic`, `part_of_speech`, `definition`, `example_sentence`, `note`, `tags`) VALUES
(1, 'abandon', '/əˈbændən/', 'v.', '放弃；抛弃', 'Don\'t abandon your dreams.', '这个词很重要', 'cet4,difficult'),
(1, 'ability', '/əˈbɪləti/', 'n.', '能力', 'She has the ability to sing.', '', 'cet4'),
(1, 'absolute', '/ˈæbsəluːt/', 'adj.', '绝对的', 'There is no absolute truth.', '注意发音', 'cet6'),
(2, 'background', '/ˈbækɡraʊnd/', 'n.', '背景', 'Tell me about your background.', '', 'daily'),
(2, 'balance', '/ˈbæləns/', 'n.', '平衡', 'Work-life balance is important.', '', 'business'),
(3, 'accomplish', '/əˈkɑːmplɪʃ/', 'v.', '完成；实现', 'We can accomplish anything.', '雅思高频', 'ielts,advanced'),
(3, 'acknowledge', '/əkˈnɑːlɪdʒ/', 'v.', '承认；致谢', 'He acknowledged his mistake.', '', 'ielts'),
(4, 'basic', '/ˈbeɪsɪk/', 'adj.', '基本的', 'These are basic skills.', '基础词汇', 'beginner'),
(5, 'ambiguous', '/æmˈbɪɡjuəs/', 'adj.', '模糊的；含糊的', 'The answer is ambiguous.', '高级词汇', 'advanced'),
(5, 'analyze', '/ˈænəlaɪz/', 'v.', '分析', 'Let\'s analyze the data.', '', 'academic');

-- ========================================
-- 11. 用户单词书-单词关联表 (user_book_word) - 将单词添加到用户的单词书中
-- ========================================
-- 用户1的生词本 (book_id=1)
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(1, 1, 1, 0, 0, 2),
(1, 1, 2, 1, 5, 1),
(1, 1, 3, 0, 2, 3);

-- 用户1的CET-4学习计划 (book_id=2) - 从公共单词书复制
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(1, 2, 4, 0, 0, 2),
(1, 2, 5, 0, 0, 2),
(1, 2, 6, 1, 3, 1);

-- 用户2的学习笔记 (book_id=3)
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(2, 3, 7, 0, 1, 2),
(2, 3, 8, 0, 0, 1);

-- 用户3的雅思备考词汇 (book_id=4)
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(3, 4, 9, 0, 0, 3),
(3, 4, 10, 0, 0, 3);

-- 用户4的基础词汇 (book_id=6)
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(4, 6, 1, 1, 10, 1),
(4, 6, 2, 1, 8, 1),
(4, 6, 4, 0, 3, 1);

-- 用户5的高级词汇 (book_id=7)
INSERT INTO `user_book_word` (`user_id`, `book_id`, `word_id`, `mastered`, `review_count`, `difficulty`) VALUES
(5, 7, 16, 0, 0, 3),
(5, 7, 17, 0, 0, 3),
(5, 7, 18, 0, 1, 3);

-- ========================================
-- 12. 商店购买记录表 (store_purchase_record) - 添加购买记录
-- ========================================
INSERT INTO `store_purchase_record` (`user_id`, `product_id`, `price_paid`, `purchase_type`, `user_book_id`) VALUES
(1, 1, 500, 1, 2),  -- 用户1购买了CET-4核心词汇
(3, 3, 800, 1, 4);  -- 用户3购买了雅思基础词汇

-- ========================================
-- 13. 秒杀活动表 (seckill_activity) - 添加秒杀活动
-- ========================================
INSERT INTO `seckill_activity` (`product_id`, `seckill_price`, `stock`, `start_time`, `end_time`) VALUES
(1, 299, 20, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY)),  -- CET-4词汇秒杀，原价500，秒杀价299
(3, 499, 10, DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY));  -- 雅思词汇即将开始秒杀

-- ========================================
-- 数据验证查询
-- ========================================
SELECT '=== 数据统计 ===' AS info;
SELECT '用户表' AS table_name, COUNT(*) AS count FROM user
UNION ALL
SELECT '公共单词表', COUNT(*) FROM public_word
UNION ALL
SELECT '公共单词书表', COUNT(*) FROM public_vocabulary_book
UNION ALL
SELECT '公共单词书-单词关联表', COUNT(*) FROM public_book_word
UNION ALL
SELECT '商店商品表', COUNT(*) FROM store_product
UNION ALL
SELECT '用户积分账户表', COUNT(*) FROM user_points_account
UNION ALL
SELECT '用户积分交易记录表', COUNT(*) FROM user_points_transaction
UNION ALL
SELECT '用户签到记录表', COUNT(*) FROM user_checkin
UNION ALL
SELECT '用户单词书表', COUNT(*) FROM user_vocabulary_book
UNION ALL
SELECT '用户单词表', COUNT(*) FROM user_word
UNION ALL
SELECT '用户单词书-单词关联表', COUNT(*) FROM user_book_word
UNION ALL
SELECT '商店购买记录表', COUNT(*) FROM store_purchase_record
UNION ALL
SELECT '秒杀活动表', COUNT(*) FROM seckill_activity;

SET FOREIGN_KEY_CHECKS = 1;
