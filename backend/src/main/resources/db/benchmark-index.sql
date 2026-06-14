-- ============================================================
-- 索引优化前后性能对比 — 测试数据 + EXPLAIN + 加索引
-- ============================================================

-- ============================================================
-- 第一步：插入 50000 条购买记录（模拟真实数据量）
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_bench_users;
CREATE TEMPORARY TABLE tmp_bench_users AS
SELECT id FROM user WHERE username LIKE 'bench_user_%' ORDER BY id;

SELECT CONCAT('测试用户数: ', COUNT(*)) AS result FROM tmp_bench_users;

-- 实际商品 ID 列表
DROP TEMPORARY TABLE IF EXISTS tmp_product_ids;
CREATE TEMPORARY TABLE tmp_product_ids AS
SELECT 1 AS pid UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14;

-- 为每个用户在多个商品上生成购买记录（50000 条）
-- 用笛卡尔积: 1000 用户 × 10 商品 × 5 条/商品 = 50000
INSERT INTO store_purchase_record (user_id, product_id, price_paid, purchase_type, user_book_id, created_at)
SELECT
    u.id,
    p.pid,
    FLOOR(RAND() * 1000) + 100 AS price_paid,
    1 AS purchase_type,
    NULL AS user_book_id,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) AS created_at
FROM tmp_bench_users u
CROSS JOIN tmp_product_ids p
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) t;

SELECT CONCAT('✅ 插入完成，购买记录总数: ', COUNT(*)) AS result FROM store_purchase_record;

-- ============================================================
-- 第二步：优化前 — 查看查询计划
-- ============================================================
SELECT '=== 🟡 BEFORE INDEX ===' AS phase;

-- 场景 A: selectByUserAndProduct（isPurchased 核心查询）
EXPLAIN SELECT * FROM store_purchase_record WHERE user_id = 19 AND product_id = 14;
EXPLAIN ANALYZE SELECT * FROM store_purchase_record WHERE user_id = 19 AND product_id = 14;

-- 场景 B: selectByFilter 中的 EXISTS 子查询（需要 reference_id 索引）
EXPLAIN SELECT sp.* FROM store_product sp
WHERE sp.status = 1
  AND EXISTS (SELECT 1 FROM public_vocabulary_book pvb WHERE pvb.id = sp.reference_id AND pvb.category = 'cet4')
ORDER BY sp.sort_order DESC, sp.created_at DESC;

-- 场景 C: selectByUserIdAndText（需要复合索引）
EXPLAIN SELECT * FROM user_word WHERE user_id = 19 AND word_text = 'apple';

-- ============================================================
-- 第三步：添加缺失索引
-- ============================================================
SELECT '=== 🔧 ADDING INDEXES ===' AS phase;

-- 3.1 store_purchase_record 复合索引（核心）
--    优化 isPurchased() → selectByUserAndProduct
ALTER TABLE store_purchase_record
    ADD INDEX idx_user_product (user_id, product_id);
SELECT '✅ idx_user_product (user_id, product_id) 已创建' AS result;

-- 3.2 store_product reference_id 索引
--    优化 EXISTS 子查询 JOIN 性能
ALTER TABLE store_product
    ADD INDEX idx_reference_id (reference_id);
SELECT '✅ idx_reference_id (reference_id) 已创建' AS result;

-- 3.3 user_word 复合索引
--    优化 selectByUserIdAndText 查询
ALTER TABLE user_word
    ADD INDEX idx_user_word_text (user_id, word_text);
SELECT '✅ idx_user_word_text (user_id, word_text) 已创建' AS result;

-- ============================================================
-- 第四步：优化后 — 查看查询计划
-- ============================================================
SELECT '=== 🟢 AFTER INDEX ===' AS phase;

-- 场景 A
EXPLAIN SELECT * FROM store_purchase_record WHERE user_id = 19 AND product_id = 14;
EXPLAIN ANALYZE SELECT * FROM store_purchase_record WHERE user_id = 19 AND product_id = 14;

-- 场景 B
EXPLAIN SELECT sp.* FROM store_product sp
WHERE sp.status = 1
  AND EXISTS (SELECT 1 FROM public_vocabulary_book pvb WHERE pvb.id = sp.reference_id AND pvb.category = 'cet4')
ORDER BY sp.sort_order DESC, sp.created_at DESC;

-- 场景 C
EXPLAIN SELECT * FROM user_word WHERE user_id = 19 AND word_text = 'apple';

-- ============================================================
-- 第五步：输出索引列表确认
-- ============================================================
SELECT '=== 📋 FINAL INDEXES ===' AS phase;
SELECT '--- store_purchase_record ---' AS '';
SHOW INDEX FROM store_purchase_record;
SELECT ''; SELECT '--- store_product ---' AS '';
SHOW INDEX FROM store_product;
SELECT ''; SELECT '--- user_word ---' AS '';
SHOW INDEX FROM user_word;
