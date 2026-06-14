# 🏪 商店功能设计方案

## 📋 目录
- [1. 功能概述](#1-功能概述)
- [2. 数据库设计](#2-数据库设计)
- [3. 核心业务流程](#3-核心业务流程)
- [4. API接口设计](#4-api接口设计)
- [5. 技术实现要点](#5-技术实现要点)
- [6. 扩展建议](#6-扩展建议)

---

## 1. 功能概述

### 1.1 核心功能
- **积分系统**：用户通过签到、学习等方式获取积分
- **商店浏览**：展示可购买的单词书（公共资源）
- **购买流程**：使用积分购买单词书，自动添加到个人单词本
- **购买记录**：查看历史购买记录

### 1.2 设计理念
- **公共表命名规范**：所有公共资源表以 `public_` 开头
- **数据隔离**：商店单词书与用户单词书分离
- **购买机制**：购买时复制单词书到用户个人空间
- **防重复购买**：唯一约束保证同一用户只能购买一次

---

## 2. 数据库设计

### 2.1 表结构总览

```
┌─────────────────────────────────┐
│     public_points_account       │  ← 用户积分账户
└─────────────────────────────────┘
            ↓
┌─────────────────────────────────┐
│  public_points_transaction      │  ← 积分交易记录
└─────────────────────────────────┘

┌─────────────────────────────────┐
│     public_book_store           │  ← 商店单词书（公共）
└─────────────────────────────────┘
            ↓
┌─────────────────────────────────┐
│     public_book_words           │  ← 商店单词关联
└─────────────────────────────────┘
            ↓ (购买)
┌─────────────────────────────────┐
│  public_user_purchased_books    │  ← 购买记录
└─────────────────────────────────┘
            ↓
┌─────────────────────────────────┐
│     vocabulary_book             │  ← 用户个人单词书
└─────────────────────────────────┘

┌─────────────────────────────────┐
│     public_user_checkin         │  ← 签到记录
└─────────────────────────────────┘
```

### 2.2 核心表详解

#### 📊 public_points_account（用户积分账户）
```sql
字段说明：
- user_id: 用户ID（唯一，一对一关系）
- balance: 当前积分余额
- total_earned: 累计获得积分（统计用）
- total_spent: 累计消费积分（统计用）

业务规则：
- 用户注册时自动创建账户，赠送100积分
- 余额不能为负数
- 每次积分变动都需要记录到 transaction 表
```

#### 💰 public_points_transaction（积分交易记录）
```sql
交易类型（type）：
1 - 注册赠送
2 - 每日签到
3 - 学习奖励
4 - 购买消费
5 - 系统调整

字段说明：
- amount: 正数表示增加，负数表示减少
- balance_after: 交易后的余额快照
- reference_id: 关联的业务ID（如购买的单词书ID）
```

#### 📚 public_book_store（商店单词书）
```sql
分类（category）：
- cet4: 大学英语四级
- cet6: 大学英语六级
- ielts: 雅思
- toefl: 托福
- business: 商务英语
- daily: 日常英语

难度（difficulty）：
1 - 初级
2 - 中级
3 - 高级

营销标签：
- is_hot: 热门
- is_new: 新品
- is_recommended: 推荐

价格策略：
- price: 当前价格
- original_price: 原价（用于显示折扣）
```

#### 🔗 public_book_words（商店单词关联）
```sql
字段说明：
- book_id: 关联商店单词书ID
- word_id: 关联单词表ID
- sort_order: 单词在书中的排序

特点：
- 这是公共资源，所有用户共享
- 购买时需要复制到用户的 book_word 表
```

#### 🛒 public_user_purchased_books（购买记录）
```sql
购买类型（purchase_type）：
1 - 正常购买
2 - 免费领取
3 - VIP赠送

防重复购买：
- UNIQUE KEY uk_user_store_book (user_id, store_book_id)
```

#### ✅ public_user_checkin（签到记录）
```sql
连续签到逻辑：
- continuous_days: 连续签到天数
- 如果昨天签到了，今天continuous_days + 1
- 如果中断，重新从1开始

奖励机制：
- 基础奖励：每天 +10 积分
- 连续7天：额外 +50 积分
- 连续30天：额外 +200 积分
```

---

## 3. 核心业务流程

### 3.1 用户注册送积分

```
用户注册
  ↓
创建积分账户（balance=100）
  ↓
记录交易（type=1, amount=+100）
  ↓
返回用户信息和初始积分
```

### 3.2 每日签到

```
用户发起签到
  ↓
检查今天是否已签到
  ↓ YES → 返回"今日已签到"
  ↓ NO
检查昨天是否签到
  ↓ YES → continuous_days + 1
  ↓ NO → continuous_days = 1
  ↓
增加积分（基础10分）
  ↓
检查连续天数奖励
  ↓ 7天 → 额外+50
  ↓ 30天 → 额外+200
  ↓
记录签到记录
  ↓
记录积分交易
  ↓
返回签到结果和获得的积分
```

### 3.3 购买单词书（核心流程）

```
用户点击购买
  ↓
1. 检查是否已购买
  ↓ YES → 返回"已拥有此单词书"
  ↓ NO
2. 检查积分余额
  ↓ 不足 → 返回"积分不足"
  ↓ 充足
3. 开启事务
  ↓
4. 扣除积分
   - 更新 points_account.balance
   - 更新 points_account.total_spent
   - 插入 transaction 记录（type=4, amount=-price）
  ↓
5. 创建用户单词书
   - 在 vocabulary_book 表插入记录
   - book_name = 商店单词书名称
   - user_id = 当前用户ID
   - word_count = 0（后续更新）
  ↓
6. 复制单词关联
   - 查询 public_book_words 中该商店书的所有单词
   - 批量插入到 book_word 表
   - 设置 user_id, book_id（新生成的）, word_id
  ↓
7. 更新单词书单词数量
   - 更新 vocabulary_book.word_count
  ↓
8. 记录购买记录
   - 插入 public_user_purchased_books
  ↓
9. 清除Redis缓存
   - 删除用户单词书列表缓存
  ↓
10. 提交事务
  ↓
返回购买成功
```

### 3.4 学习奖励积分

```
用户完成学习任务
  ↓
判断任务类型
  ↓
- 掌握10个单词 → +5积分
- 完成每日学习目标 → +20积分
- 连续学习7天 → +50积分
  ↓
更新积分账户
  ↓
记录交易（type=3）
  ↓
返回奖励结果
```

---

## 4. API接口设计

### 4.1 积分相关

#### GET /api/points/balance
**获取当前积分余额**
```json
响应：
{
  "code": 200,
  "data": {
    "balance": 1250,
    "totalEarned": 1500,
    "totalSpent": 250
  }
}
```

#### POST /api/points/checkin
**每日签到**
```json
响应：
{
  "code": 200,
  "data": {
    "checkedIn": true,
    "pointsEarned": 10,
    "continuousDays": 5,
    "bonusPoints": 0
  }
}
```

#### GET /api/points/transactions?page=1&size=20
**获取积分交易记录**
```json
响应：
{
  "code": 200,
  "data": {
    "total": 50,
    "list": [
      {
        "id": 1,
        "type": 4,
        "typeName": "购买消费",
        "amount": -200,
        "balanceAfter": 1250,
        "description": "购买单词书：CET-4 核心词汇",
        "createdAt": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

### 4.2 商店相关

#### GET /api/store/books?category=cet4&difficulty=1&page=1&size=20
**获取商店单词书列表**
```json
参数：
- category: 分类筛选（可选）
- difficulty: 难度筛选（可选）
- sortBy: 排序方式（price/hot/new/recommend）
- page: 页码
- size: 每页数量

响应：
{
  "code": 200,
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "bookName": "CET-4 核心词汇",
        "description": "大学英语四级考试核心词汇",
        "coverImage": "/images/cet4.jpg",
        "category": "cet4",
        "difficulty": 1,
        "wordCount": 1500,
        "price": 200,
        "originalPrice": 300,
        "isHot": 1,
        "isNew": 0,
        "isRecommended": 1,
        "isPurchased": false
      }
    ]
  }
}
```

#### GET /api/store/books/{id}
**获取单词书详情**
```json
响应：
{
  "code": 200,
  "data": {
    "id": 1,
    "bookName": "CET-4 核心词汇",
    "description": "...",
    "wordCount": 1500,
    "price": 200,
    "sampleWords": [
      {"wordText": "apple", "definition": "苹果"},
      {"wordText": "book", "definition": "书"}
    ]
  }
}
```

#### POST /api/store/books/{id}/purchase
**购买单词书**
```json
响应：
{
  "code": 200,
  "msg": "购买成功",
  "data": {
    "userBookId": 123,
    "remainingBalance": 1050
  }
}
```

### 4.3 购买记录

#### GET /api/store/purchases?page=1&size=20
**获取购买记录**
```json
响应：
{
  "code": 200,
  "data": {
    "total": 10,
    "list": [
      {
        "id": 1,
        "storeBookId": 1,
        "bookName": "CET-4 核心词汇",
        "pricePaid": 200,
        "purchaseType": 1,
        "createdAt": "2024-01-01 12:00:00"
      }
    ]
  }
}
```

---

## 5. 技术实现要点

### 5.1 事务处理

购买单词书涉及多个表操作，必须使用事务保证数据一致性：

```java
@Transactional
public void purchaseBook(Long userId, Long storeBookId) {
    // 1. 检查是否已购买
    // 2. 检查积分余额
    // 3. 扣除积分
    // 4. 创建用户单词书
    // 5. 复制单词关联
    // 6. 记录购买记录
    // 7. 清除缓存
}
```

### 5.2 并发控制

防止用户快速点击导致重复购买：
- 数据库唯一约束：`uk_user_store_book`
- Redis分布式锁（可选）：`lock:purchase:{userId}:{storeBookId}`

### 5.3 性能优化

1. **批量插入单词关联**
```java
// 不要逐条插入，使用批量插入
bookWordMapper.batchInsert(bookWordList);
```

2. **异步处理非关键操作**
```java
// 发送积分变动通知可以异步
@Async
public void sendPointsNotification(Long userId, int amount) {
    // 发送WebSocket或推送通知
}
```

3. **缓存策略**
```java
// 商店单词书列表缓存（变化不频繁）
redisUtil.set("store:books:page:" + page, books, 1, TimeUnit.HOURS);

// 用户购买状态缓存
redisUtil.set("user:purchased:" + userId + ":" + bookId, true, 1, TimeUnit.DAYS);
```

### 5.4 积分计算工具类

```java
@Component
public class PointsCalculator {
    
    /**
     * 计算签到奖励积分
     */
    public int calculateCheckinPoints(int continuousDays) {
        int basePoints = 10;
        int bonusPoints = 0;
        
        if (continuousDays == 7) {
            bonusPoints = 50;
        } else if (continuousDays == 30) {
            bonusPoints = 200;
        }
        
        return basePoints + bonusPoints;
    }
    
    /**
     * 计算学习奖励积分
     */
    public int calculateLearningPoints(String taskType, int count) {
        switch (taskType) {
            case "master_words":
                return (count / 10) * 5; // 每掌握10个单词+5分
            case "daily_goal":
                return 20;
            default:
                return 0;
        }
    }
}
```

---

## 6. 扩展建议

### 6.1 短期优化（1-2周）

1. **单词书预览功能**
   - 购买前可查看前10个单词
   - 显示单词书的详细信息和评价

2. **积分排行榜**
   - 每周/每月积分排行
   - 激励用户学习和签到

3. **优惠券系统**
   - 新用户优惠券
   - 活动优惠券

### 6.2 中期规划（1-2月）

1. **VIP会员制度**
   - 月费/年费会员
   - 会员免费获取部分单词书
   - 积分加倍特权

2. **单词书评价系统**
   - 用户可以对购买的单词书评分
   - 评论和分享学习心得

3. **限时促销活动**
   - 打折活动
   - 买一送一
   - 节日特惠

### 6.3 长期规划（3-6月）

1. **社交功能**
   - 好友系统
   - 赠送单词书给好友
   - 组队学习

2. **成就系统**
   - 学习里程碑
   - 勋章收集
   - 等级提升

3. **AI智能推荐**
   - 根据用户水平推荐单词书
   - 个性化学习计划

---

## 7. 注意事项

### 7.1 数据安全
- 积分变动必须记录日志，便于追溯
- 重要操作需要二次确认（如大额积分消费）
- 防止SQL注入和XSS攻击

### 7.2 用户体验
- 积分不足时提供快速获取积分的引导
- 购买成功后立即刷新用户单词书列表
- 提供清晰的购买记录和积分明细

### 7.3 测试要点
- 并发购买测试
- 积分边界值测试（余额为0、刚好够买等）
- 事务回滚测试
- 缓存一致性测试

---

## 8. 总结

这个商店功能设计方案具有以下特点：

✅ **清晰的表结构设计**：公共表与用户表分离  
✅ **完整的事务处理**：保证数据一致性  
✅ **灵活的扩展性**：支持后续添加新功能  
✅ **良好的用户体验**：防重复购买、实时反馈  
✅ **完善的日志记录**：积分变动可追溯  

下一步可以开始实现后端Service层和Controller层的代码了！
