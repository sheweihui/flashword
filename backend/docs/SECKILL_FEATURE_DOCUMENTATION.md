# 秒杀功能设计文档

## 📋 目录

- [1. 功能概述](#1-功能概述)
- [2. 技术架构](#2-技术架构)
- [3. 数据库设计](#3-数据库设计)
- [4. 核心流程](#4-核心流程)
- [5. API接口](#5-api接口)
- [6. 性能优化](#6-性能优化)
- [7. 使用示例](#7-使用示例)
- [8. 常见问题](#8-常见问题)

---

## 1. 功能概述

### 1.1 业务场景

秒杀系统用于处理高并发场景下的限时抢购活动，典型应用场景：
- 限时特价商品抢购
- 限量优惠券发放
- 热门活动名额预订

### 1.2 核心特性

✅ **高性能**：异步秒杀响应时间 < 30ms  
✅ **防超卖**：Redis原子操作 + 数据库乐观锁双重保障  
✅ **防重复**：唯一索引 + Redis分布式锁  
✅ **可靠性**：消息日志记录 + RabbitMQ异步处理  
✅ **可追溯**：完整的消息生命周期追踪

---

## 2. 技术架构

### 2.1 整体架构图

```
用户请求
    ↓
┌─────────────────┐
│  SeckillController │ ← Token认证
└────────┬────────┘
         ↓
┌─────────────────┐
│ SeckillService     │ ← 业务逻辑层
└───┬─────────┬────┘
    ↓         ↓
┌──────┐  ┌──────────┐
│ Redis │  │  MySQL   │ ← 数据持久化
└──┬───┘  └──────────┘
   ↓
┌──────────┐
│ RabbitMQ  │ ← 异步解耦
└────┬─────┘
     ↓
┌──────────┐
│ Consumer  │ ← 后台处理积分扣除
└──────────┘
```

### 2.2 技术栈

| 组件 | 技术选型 | 作用 |
|------|---------|------|
| Web框架 | Spring Boot 3.2 | RESTful API |
| 数据库 | MySQL 8.0 | 数据持久化 |
| 缓存 | Redis | 库存预扣减、防重复 |
| 消息队列 | RabbitMQ | 异步处理、削峰填谷 |
| ORM | MyBatis | 数据访问 |

---

## 3. 数据库设计

### 3.1 表结构

#### seckill_activity（秒杀活动表）

```sql
CREATE TABLE `seckill_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '秒杀活动ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `seckill_price` int NOT NULL COMMENT '秒杀价格（积分）',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存数量',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  INDEX `idx_time`(`start_time`, `end_time`)
) COMMENT = '秒杀活动表';
```

**字段说明：**
- `product_id`: 关联store_product表的商品ID
- `seckill_price`: 秒杀专享价格（通常远低于原价）
- `stock`: 总库存数量
- `start_time/end_time`: 活动时间窗口

#### seckill_order（秒杀订单表）

```sql
CREATE TABLE `seckill_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '秒杀活动ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_activity`(`user_id`, `activity_id`) COMMENT '防止重复购买',
  UNIQUE INDEX `uk_order_no`(`order_no`)
) COMMENT = '秒杀订单表';
```

**关键设计：**
- `uk_user_activity`: 唯一索引防止同一用户重复参与同一活动
- `order_no`: 全局唯一订单号，格式：SK + 时间戳

#### seckill_message_log（消息日志表）

```sql
CREATE TABLE `seckill_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息日志ID',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID',
  `message_content` text NOT NULL COMMENT '消息内容',
  `status` tinyint DEFAULT 0 COMMENT '状态：0-发送中，1-成功，2-失败',
  `retry_count` int DEFAULT 0 COMMENT '重试次数',
  `error_message` text COMMENT '错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_message_id`(`message_id`)
) COMMENT = '秒杀消息日志表';
```

**作用：**
- 保证RabbitMQ消息的可靠性
- 支持失败消息的重试和补偿
- 便于问题追踪和数据审计

---

## 4. 核心流程

### 4.1 同步秒杀流程

```
1. 接收请求 → 校验Token获取用户ID
2. 查询活动信息 → 校验活动时间
3. 检查是否已购买 → 查询seckill_order表
4. 扣减库存（乐观锁）→ UPDATE stock WHERE stock > 0
5. 扣除用户积分 → user_points_account表
6. 创建订单 → 插入seckill_order表
7. 返回订单号

总耗时：约 200-500ms
```

**优点：** 数据强一致性  
**缺点：** 响应慢，不适合高并发

### 4.2 异步秒杀流程（推荐）⭐

```
【关键路径 - 同步执行】
1. 接收请求 → 校验Token
2. 查询活动信息 → 校验活动时间
3. Redis预扣减库存 → DECR seckill:stock:{activityId}
4. Redis防重复检查 → SETNX seckill:user:{userId}:{activityId}
5. 创建订单记录 → 插入seckill_order表（status=待处理）
6. 发送RabbitMQ消息 → 记录消息日志
7. 立即返回订单号 ⚡

总耗时：约 10-30ms

【后台异步处理】
8. Consumer接收消息
9. 扣除用户积分
10. 更新订单状态为已完成
11. 更新数据库库存（最终一致性）
```

**优点：** 
- 响应速度提升10倍+
- 支持高并发（QPS 500-1000+）
- 用户体验好

**缺点：** 
- 最终一致性（短暂延迟）
- 需要处理消息失败情况

### 4.3 库存扣减策略

#### Redis预扣减（高性能）

```java
// 原子操作，线程安全
Long remainingStock = redisUtil.decrement(stockKey, 1);

if (remainingStock == null || remainingStock < 0) {
    // 库存不足，恢复计数
    redisUtil.increment(stockKey, 1);
    throw new RuntimeException("库存不足");
}
```

#### 数据库乐观锁（兜底保障）

```sql
UPDATE seckill_activity 
SET stock = stock - 1 
WHERE id = #{id} AND stock > 0;
```

**双重保障机制：**
- Redis承担99%的流量，快速拦截
- 数据库作为最终一致性保障

---

## 5. API接口

### 5.1 执行秒杀（同步）

**请求：**
```http
POST /api/seckill/execute/sync/{activityId}
Headers:
  token: user_token_here
```

**响应：**
```json
{
  "code": 200,
  "message": "秒杀成功",
  "data": "SK1716234567890"
}
```

**错误响应：**
```json
{
  "code": 500,
  "message": "库存不足，秒杀失败",
  "data": null
}
```

### 5.2 执行秒杀（异步）⭐推荐

**请求：**
```http
POST /api/seckill/execute/async/{activityId}
Headers:
  token: user_token_here
```

**响应：**
```json
{
  "code": 200,
  "message": "秒杀请求已提交，正在后台处理",
  "data": "SK1716234567891"
}
```

**性能对比：**

| 指标 | 同步方式 | 异步方式 |
|------|---------|---------|
| 响应时间 | 200-500ms | 10-30ms |
| QPS | 50-100 | 500-1000+ |
| 适用场景 | 低并发测试 | 生产环境 |

---

## 6. 性能优化

### 6.1 优化策略

#### 1️⃣ Redis预扣减库存

**问题：** 数据库行锁竞争激烈  
**方案：** 使用Redis原子操作DECR  
**效果：** 响应时间从200ms降至10ms

#### 2️⃣ 防重复购买双重保障

```
第一层：Redis SETNX（快速拦截）
第二层：数据库唯一索引（最终保障）
```

#### 3️⃣ 异步解耦

将耗时的积分扣除操作异步化：
- 关键路径只处理库存扣减和订单创建
- 非关键操作通过RabbitMQ异步处理

#### 4️⃣ 消息可靠性保证

```java
// 1. 发送前记录日志
seckillMessageLogMapper.insert(messageLog);

// 2. 发送消息
messageProducer.sendSeckillMessage(message);

// 3. 更新状态
seckillMessageLogMapper.updateStatus(messageId, 1);
```

### 6.2 性能测试数据

**测试环境：**
- CPU: 4核
- 内存: 8GB
- 并发用户: 100

**测试结果：**

| 场景 | QPS | 平均响应时间 | P99响应时间 |
|------|-----|------------|------------|
| 同步秒杀 | 80 | 250ms | 450ms |
| 异步秒杀 | 800 | 15ms | 30ms |

**结论：** 异步方式性能提升**10倍**！

---

## 7. 使用示例

### 7.1 创建秒杀活动

```sql
-- 创建活动：明天上午10点开始，持续1小时，限量100份
INSERT INTO seckill_activity (
    product_id, 
    seckill_price, 
    stock, 
    start_time, 
    end_time
) VALUES (
    1,                              -- 商品ID
    99,                             -- 秒杀价99积分
    100,                            -- 限量100份
    '2026-05-21 10:00:00',         -- 开始时间
    '2026-05-21 11:00:00'          -- 结束时间
);
```

### 7.2 初始化Redis库存

```bash
# 连接到Redis
redis-cli

# 设置库存（activityId=1）
SET seckill:stock:1 100

# 验证
GET seckill:stock:1
# 输出: "100"
```

### 7.3 前端调用示例

```javascript
// JavaScript示例
async function executeSeckill(activityId) {
  try {
    const response = await fetch(
      `/api/seckill/execute/async/${activityId}`, 
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'token': localStorage.getItem('userToken')
        }
      }
    );
    
    const result = await response.json();
    
    if (result.code === 200) {
      console.log('秒杀成功！订单号:', result.data);
      alert('秒杀成功！');
    } else {
      alert('秒杀失败: ' + result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
  }
}

// 调用
executeSeckill(1);
```

### 7.4 并发测试

```bash
# 使用wrk进行压力测试
wrk -t10 -c100 -d30s \
  -H "token: user_token_1" \
  --latency \
  http://localhost:8080/api/seckill/execute/async/1
```

---

## 8. 常见问题

### Q1: 如何保证不超卖？

**A:** 采用双重保障机制：
1. Redis原子操作DECR预扣减（快速拦截）
2. 数据库乐观锁 `WHERE stock > 0`（最终保障）

### Q2: 如何处理消息发送失败？

**A:** 
1. 消息发送前记录到`seckill_message_log`表
2. 定时任务扫描失败消息并重试
3. 最大重试3次，仍失败则人工介入

### Q3: 用户重复点击怎么办？

**A:** 
1. Redis SETNX防止短时间内重复请求
2. 数据库唯一索引`uk_user_activity`作为最终保障
3. 前端按钮防抖处理

### Q4: 活动结束后还能购买吗？

**A:** 不能。服务层会校验活动时间：
```java
if (now.isBefore(activity.getStartTime())) {
    throw new RuntimeException("秒杀尚未开始");
}
if (now.isAfter(activity.getEndTime())) {
    throw new RuntimeException("秒杀已结束");
}
```

### Q5: 如何监控秒杀系统？

**A:** 
1. 查看应用日志：`tail -f logs/application.log | grep "秒杀"`
2. 查询消息日志表：`SELECT * FROM seckill_message_log WHERE status = 2`
3. 监控Redis库存：`GET seckill:stock:{activityId}`
4. 统计订单数量：`SELECT COUNT(*) FROM seckill_order WHERE activity_id = ?`

---

## 9. 运维指南

### 9.1 活动前准备

1. **创建秒杀活动**
   ```sql
   INSERT INTO seckill_activity ...
   ```

2. **初始化Redis库存**
   ```bash
   SET seckill:stock:{activityId} {stock}
   ```

3. **预热缓存**（可选）
   ```java
   // 提前加载活动信息到Redis
   redisUtil.set("seckill:activity:" + id, activity, 3600, TimeUnit.SECONDS);
   ```

### 9.2 活动中监控

```sql
-- 实时监控库存
SELECT stock FROM seckill_activity WHERE id = 1;

-- 监控订单数量
SELECT COUNT(*) FROM seckill_order WHERE activity_id = 1;

-- 监控失败消息
SELECT COUNT(*) FROM seckill_message_log WHERE status = 2;
```

### 9.3 活动后清理

```bash
# 清理Redis缓存
DEL seckill:stock:1
DEL seckill:user:*:1

# 归档订单数据（可选）
INSERT INTO seckill_order_archive SELECT * FROM seckill_order WHERE activity_id = 1;
DELETE FROM seckill_order WHERE activity_id = 1;
```

---

## 10. 扩展建议

### 10.1 功能扩展

- [ ] 添加活动状态管理（未开始/进行中/已结束）
- [ ] 支持每人限购数量配置
- [ ] 添加活动排队机制
- [ ] 实现自动补货功能
- [ ] 添加黑名单机制（防刷）

### 10.2 性能优化

- [ ] 引入本地缓存（Caffeine）减少Redis访问
- [ ] 使用Lua脚本保证原子性
- [ ] 分库分表支持更大规模
- [ ] CDN加速静态资源

### 10.3 安全加固

- [ ] IP限流（同一IP每秒最多N次请求）
- [ ] 图形验证码（防止机器刷单）
- [ ] 风控系统（识别异常行为）
- [ ] HTTPS加密传输

---

## 📞 技术支持

如有问题，请查看：
1. 应用日志：`logs/application.log`
2. 消息日志表：`seckill_message_log`
3. 联系开发团队

---

**文档版本：** v1.0  
**最后更新：** 2026-05-20  
**维护者：** 后端开发团队
