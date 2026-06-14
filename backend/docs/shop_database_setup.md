# 🗄️ 商店功能数据库初始化指南

## 📋 执行顺序

### 方式一：完整初始化（推荐）

如果你还没有创建数据库，按以下顺序执行：

```bash
# 1. 执行主schema（创建基础表结构）
mysql -u root -p < src/main/resources/db/schema.sql

# 2. 执行商店功能SQL（创建商店相关表和测试数据）
mysql -u root -p < src/main/resources/db/shop_feature.sql
```

### 方式二：已有数据库

如果数据库已经存在，只需执行：

```bash
# 直接执行商店功能SQL
mysql -u root -p vocabulary_app < src/main/resources/db/shop_feature.sql
```

或者在 MySQL 客户端中：

```sql
USE vocabulary_app;
SOURCE D:/aaaa_Project/agent/src/main/resources/db/shop_feature.sql;
```

## 🔧 修复说明

### 问题
之前执行时遇到外键约束错误：
```
1452 - Cannot add or update a child row: a foreign key constraint fails
```

### 原因
`public_book_words` 表需要引用 `word` 表中的单词ID，但 `word` 表可能还没有测试数据。

### 解决方案
在 `shop_feature.sql` 中添加了 `INSERT IGNORE` 语句，确保 `word` 表中有 ID 为 1-5 的测试单词：
- apple (ID: 1)
- book (ID: 2)
- computer (ID: 3)
- hello (ID: 4)
- world (ID: 5)

`INSERT IGNORE` 的好处：
- 如果单词已存在，跳过不插入
- 如果单词不存在，则插入
- 不会报错或中断执行

## ✅ 验证安装

执行完成后，可以通过以下SQL验证：

```sql
-- 1. 检查商店单词书是否创建成功
SELECT id, book_name, price, word_count FROM public_book_store;

-- 2. 检查单词关联是否创建成功
SELECT pb.book_id, ps.book_name, pb.word_id, w.word_text 
FROM public_book_words pb
JOIN public_book_store ps ON pb.book_id = ps.id
JOIN word w ON pb.word_id = w.id;

-- 3. 检查视图是否创建成功
SELECT * FROM view_user_points_summary LIMIT 5;
SELECT * FROM view_store_book_detail LIMIT 5;

-- 4. 检查触发器是否创建成功
SHOW TRIGGERS LIKE 'trg_%';
```

预期结果：
- `public_book_store` 应该有 8 条记录
- `public_book_words` 应该有 5 条记录（第一个单词书的示例单词）
- 视图和触发器应该都能正常查询

## 🎯 下一步

数据库初始化完成后，可以开始创建 Java 实体类：

1. **Entity 实体类**
   - PointsAccount
   - PointsTransaction
   - BookStore
   - PublicBookWords
   - UserPurchasedBooks
   - UserCheckin

2. **Mapper 接口**
   - 使用 MyBatis-Plus 简化开发

3. **Service 服务层**
   - PointsService（积分管理）
   - StoreService（商店业务）
   - CheckinService（签到功能）

4. **Controller 控制器**
   - 提供 REST API 接口

需要我帮你生成这些代码吗？😊
