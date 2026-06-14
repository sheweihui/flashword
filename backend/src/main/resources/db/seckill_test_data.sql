-- ==================== 秒杀功能测试数据 ====================

-- 说明：此脚本用于初始化秒杀活动测试数据
-- 使用前请确保 store_product 表中已有商品数据

-- 1. 插入秒杀活动（假设商品ID=1和2已存在）
INSERT INTO seckill_activity (product_id, seckill_price, stock, start_time, end_time)
VALUES 
-- 进行中的秒杀活动
(1, 199, 50, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR)),
-- 即将开始的秒杀活动
(2, 299, 30, DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR)),
-- 已结束的秒杀活动
(3, 149, 20, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 2. 查看数据
SELECT sa.*, sp.product_name, sp.original_price
FROM seckill_activity sa
LEFT JOIN store_product sp ON sa.product_id = sp.id
ORDER BY sa.start_time ASC;

-- 3. 初始化Redis库存（在应用启动后执行）
-- 在Redis中执行:
-- SET seckill:stock:1 50
-- SET seckill:stock:2 30
-- SET seckill:stock:3 20
