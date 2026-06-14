# 词汇学习应用 - 数据库重新设计说明

## 📋 设计概述

本次重新设计按照模块化原则，将数据库表分为三个清晰的模块：

### 🎯 命名规范

| 模块 | 前缀 | 说明 |
|------|------|------|
| **用户模块** | `user_` | 用户个人数据（单词、单词书、积分等） |
| **商店模块** | `store_` | 商店商品和购买记录 |
| **公共模块** | `public_` | 公共资源数据（公共单词书、公共单词） |

---

## 🗂️ 表结构详解

### 1️⃣ 基础表

#### `user` - 用户表
- 存储用户基本信息
- 所有其他表都通过 `user_id` 关联到此表

---

### 2️⃣ 用户模块 (user_)

#### `user_word` - 用户单词表（生词本）
**用途**：存储用户自己添加的单词

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 单词ID |
| user_id | bigint | 用户ID |
| word_text | varchar(100) | 单词文本 |
| phonetic | varchar(100) | 音标 |
| part_of_speech | varchar(50) | 词性 |
| definition | text | 中文释义 |
| example_sentence | text | 例句 |
| example_translation | text | 例句翻译 |
| audio_url | varchar(255) | 发音音频URL |
| note | text | 个人笔记 |
| tags | varchar(255) | 标签 |

**特点**：
- 每个用户的单词独立存储
- 支持个人笔记和标签
- 与公共单词完全隔离

---

#### `user_vocabulary_book` - 用户单词书表
**用途**：存储用户创建的单词书

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 单词书ID |
| user_id | bigint | 用户ID |
| book_name | varchar(100) | 单词书名称 |
| description | text | 描述 |
| cover_image | varchar(255) | 封面图片 |
| word_count | int | 单词数量（自动更新） |
| is_public | tinyint | 是否公开 |
| source_type | tinyint | 来源：1-手动创建，2-从商店购买 |
| source_store_book_id | bigint | 来源商店单词书ID |

**特点**：
- 区分手动创建和从商店购买的单词书
- 记录来源信息，方便追溯

---

#### `user_book_word` - 用户单词书-单词关联表
**用途**：记录用户在单词书中学习单词的进度

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 记录ID |
| user_id | bigint | 用户ID |
| book_id | bigint | 用户单词书ID |
| word_id | bigint | 用户单词ID |
| mastered | tinyint | 是否掌握：0-未掌握，1-已掌握 |
| review_count | int | 复习次数 |
| last_review_time | datetime | 最后复习时间 |
| difficulty | tinyint | 难度等级：1-简单，2-中等，3-困难 |
| priority | int | 学习优先级 |

**特点**：
- 记录学习状态和进度
- 支持复习追踪
- 触发器自动更新单词书的 `word_count`

---

#### `user_points_account` - 用户积分账户表
**用途**：存储用户积分余额

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 账户ID |
| user_id | bigint | 用户ID（唯一） |
| balance | int | 当前积分余额 |
| total_earned | int | 累计获得积分 |
| total_spent | int | 累计消费积分 |

---

#### `user_points_transaction` - 用户积分交易记录表
**用途**：记录每次积分变动

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 交易ID |
| user_id | bigint | 用户ID |
| type | tinyint | 交易类型：1-注册赠送，2-签到，3-学习奖励，4-购买消费，5-系统调整 |
| amount | int | 积分变化量（正数增加，负数减少） |
| balance_after | int | 交易后余额 |
| description | varchar(255) | 交易描述 |
| reference_id | bigint | 关联ID |

---

#### `user_checkin` - 用户签到记录表
**用途**：记录用户每日签到

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 签到ID |
| user_id | bigint | 用户ID |
| checkin_date | date | 签到日期 |
| continuous_days | int | 连续签到天数 |
| points_earned | int | 获得的积分 |

**特点**：
- 联合唯一索引防止重复签到
- 自动计算连续签到天数

---

### 3️⃣ 公共模块 (public_)

#### `public_word` - 公共单词表
**用途**：存储商店单词书中的标准单词

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 公共单词ID |
| word_text | varchar(100) | 单词文本 |
| phonetic | varchar(100) | 音标 |
| part_of_speech | varchar(50) | 词性 |
| definition | text | 中文释义 |
| example_sentence | text | 例句 |
| example_translation | text | 例句翻译 |
| audio_url | varchar(255) | 发音音频URL |
| difficulty_level | tinyint | 难度等级：1-简单，2-中等，3-困难 |
| frequency_rank | int | 词频排名 |
| tags | varchar(255) | 标签（如：cet4,core） |

**特点**：
- 与用户单词完全独立
- 包含扩展字段（难度、词频、标签）
- 标准化数据，便于管理

---

#### `public_vocabulary_book` - 公共单词书表
**用途**：存储标准化的单词书（如CET-4、雅思等）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 单词书ID |
| book_name | varchar(100) | 单词书名称 |
| description | text | 描述 |
| cover_image | varchar(255) | 封面图片 |
| category | varchar(50) | 分类：cet4/cet6/ielts/toefl/business/daily |
| difficulty | tinyint | 难度等级：1-初级，2-中级，3-高级 |
| word_count | int | 单词数量（自动更新） |

**特点**：
- 不包含价格、销售等商业信息
- 纯粹的知识点组织

---

#### `public_book_word` - 公共单词书-单词关联表
**用途**：定义公共单词书包含哪些单词

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 记录ID |
| book_id | bigint | 公共单词书ID |
| word_id | bigint | 公共单词ID |
| sort_order | int | 单词在书中的排序 |

**特点**：
- 触发器自动更新 `public_vocabulary_book` 的 `word_count`
- 支持排序

---

### 4️⃣ 商店模块 (store_)

#### `store_product` - 商店商品表
**用途**：管理上架销售的商品

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 商品ID |
| product_name | varchar(100) | 商品名称 |
| product_type | tinyint | 商品类型：1-单词书 |
| reference_id | bigint | 关联ID（如公共单词书ID） |
| description | text | 商品描述 |
| cover_image | varchar(255) | 封面图片 |
| price | int | 价格（积分） |
| original_price | int | 原价（积分） |
| is_hot | tinyint | 是否热门 |
| is_new | tinyint | 是否新品 |
| is_recommended | tinyint | 是否推荐 |
| sort_order | int | 排序权重 |
| status | tinyint | 状态：0-下架，1-上架 |
| stock | int | 库存（-1表示无限） |
| sales_count | int | 销售数量 |

**特点**：
- 通过 `reference_id` 关联到 `public_vocabulary_book`
- 包含完整的电商属性（价格、库存、销量等）
- 支持上下架管理

---

#### `store_purchase_record` - 商店购买记录表
**用途**：记录用户的购买行为

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 购买记录ID |
| user_id | bigint | 用户ID |
| product_id | bigint | 商品ID |
| price_paid | int | 购买时支付的价格 |
| purchase_type | tinyint | 购买类型：1-正常购买，2-免费领取，3-VIP赠送 |
| user_book_id | bigint | 购买后生成的用户单词书ID |

**特点**：
- 记录购买时的价格（应对后续价格调整）
- 关联到生成的用户单词书

---

## 🔄 数据流转示例

### 场景：用户购买单词书

1. **浏览商品**：用户查看 `store_product` 中的商品
2. **查看详情**：通过 `reference_id` 查看 `public_vocabulary_book` 和 `public_word` 的内容
3. **购买**：
   - 在 `user_vocabulary_book` 中创建新的单词书（`source_type=2`）
   - 从 `public_book_word` 复制关联关系到 `user_book_word`
   - 从 `public_word` 复制单词到 `user_word`
   - 在 `store_purchase_record` 记录购买
   - 在 `user_points_account` 扣除积分
   - 在 `user_points_transaction` 记录交易

### 场景：用户添加生词

1. **添加单词**：在 `user_word` 中插入新单词
2. **添加到单词书**：在 `user_book_word` 中建立关联
3. **触发器自动更新**：`user_vocabulary_book.word_count` 自动增加

---

## ✅ 设计优势

### 1. **清晰的职责分离**
- 用户数据和公共数据完全隔离
- 避免混淆和管理混乱

### 2. **灵活的数据管理**
- 公共单词可以独立维护和更新
- 用户单词不受公共数据变化影响

### 3. **完整的购买流程**
- 从商品展示到购买完成的全链路追踪
- 保留购买时的快照数据

### 4. **可扩展性强**
- 易于添加新的商品类型
- 支持多种购买方式（购买、免费领取、VIP赠送）

### 5. **自动化维护**
- 触发器自动更新单词数量
- 减少人工维护成本

---

## 📊 测试数据说明

脚本包含了完整的测试数据：

### 公共模块
- **50个公共单词**：20个CET-4 + 15个CET-6 + 15个雅思
- **6本公共单词书**：涵盖不同类别和难度
- **智能关联**：通过标签自动关联单词

### 商店模块
- **6个商品**：对应6本公共单词书
- 包含价格、折扣、热门标记等

### 用户模块
- **3个测试用户**：test_user、john_doe、alice
- **10个用户自定义单词**：高级词汇
- **4本用户单词书**：不同类型的学习计划
- **完整的学习记录**：掌握状态、复习次数等
- **积分系统**：账户余额、交易记录、签到记录
- **购买记录**：模拟真实购买流程

---

## 🚀 使用指南

### 执行SQL脚本

```bash
mysql -u root -p vocabulary_app < vocabulary_app_redesign.sql
```

### 验证数据

脚本末尾包含多个统计查询，可以直接查看：
- 各模块数据统计
- 用户详细信息
- 学习进度统计

---

## 📝 注意事项

1. **外键约束**：所有表都有适当的外键约束，确保数据一致性
2. **级联删除**：删除用户时，相关数据会自动清理
3. **触发器**：自动维护单词数量，无需手动更新
4. **索引优化**：为常用查询字段建立了索引

---

## 🎯 下一步工作

1. 创建对应的 Entity 实体类
2. 创建 Mapper 接口和 XML 文件
3. 创建 Service 业务逻辑层
4. 创建 Controller API 接口
5. 编写单元测试

---

**设计完成时间**：2026-05-16  
**版本**：v2.0 - 模块化重构版
