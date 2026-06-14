# 待办事项

## MQ 容错（高优先级）

### 1. 所有消费者加上统一的重试上限
- [ ] `consumeUserLogin` — 当前无限 requeue
- [ ] `consumePointsReward` — 当前无限 requeue
- [ ] `consumeNotification` — 当前无限 requeue
- [ ] `consumePurchase` — 当前无限 requeue，且 TODO 代码路径未完成
- [ ] 参考 `consumeSeckill` 的 3 次重试模式，统一抽取成工具方法

### 2. 死信队列（DLQ）
- [ ] 配置死信交换机 + 死信队列（全局或按业务分）
- [ ] 超过重试上限的消息路由到 DLQ
- [ ] DLQ 消费者：记录日志 + 写入 `mq_message_log` 表（告警/人工处理）

### 3. `CreateUserAccount` 消息体修复
- [ ] 改成 Map 包装，不要裸传 `Long`（Jackson 反序列化可能转成 `Integer` 强转失败）

### 4. `consumePurchase` 中的 TODO 收尾
- [ ] `updateVocabularyBook` 调用修复（当前全传 null）
- [ ] `updateWordCount` 方法实现
- [ ] `updateSalesCount` 调用实现

### 5. 全局 `@RabbitMqMessage` 注解补全
- [ ] `consumeUserLogin` 加注解
- [ ] `consumePointsReward` 加注解
- [ ] `consumeNotification` 加注解
- [ ] `consumePurchase` 加注解
- [ ] `consumeSeckill` 加注解

## 积分一致性

- [ ] `purchaseBook()` 中 Redis 扣积分后，发 MQ 异步落库到 MySQL（`user_points_account`）
- [ ] 定时对账任务：对比 Redis 和 MySQL 积分余额，不一致以 MySQL 为准

## 测试

- [ ] Service 层单元测试：购买、签到、秒杀
- [ ] 集成测试：MQ 消息收发
- [ ] GitHub Actions CI 配置

## 低优先级

- [ ] 给 `StoreProductMapper.xml` 的 `${orderBy}` 改为安全方式（目前值是内部可控的，但写法不推荐）
- [ ] 加 SpringDoc/Swagger 接口文档
