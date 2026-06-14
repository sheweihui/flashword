# 📱 商店功能前端接口文档

## 📋 目录
- [1. 积分相关接口](#1-积分相关接口)
- [2. 签到相关接口](#2-签到相关接口)
- [3. 商店相关接口](#3-商店相关接口)
- [4. 数据结构说明](#4-数据结构说明)

---

## 1. 积分相关接口

### 1.1 获取当前积分余额

**接口地址**: `GET /api/store/points/balance`

**请求头**:
```
Authorization: Bearer {token}
```

**请求参数**: 无

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "balance": 1250,
    "totalEarned": 1500,
    "totalSpent": 250
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| balance | Integer | 当前积分余额 |
| totalEarned | Integer | 累计获得积分 |
| totalSpent | Integer | 累计消费积分 |

**前端使用建议**:
- 在用户进入商店页面时调用此接口
- 在顶部导航栏显示当前积分余额
- 积分不足时禁用购买按钮

---

## 2. 签到相关接口

### 2.1 每日签到

**接口地址**: `POST /api/store/checkin`

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数**: 无（Body为空或 `{}`）

**响应示例 - 签到成功**:
```json
{
  "code": 200,
  "msg": "签到成功！获得10积分，连续签到5天",
  "data": {
    "checkedIn": true,
    "pointsEarned": 10,
    "continuousDays": 5,
    "bonusPoints": 0,
    "checkinDate": "2024-01-15"
  }
}
```

**响应示例 - 连续7天奖励**:
```json
{
  "code": 200,
  "msg": "签到成功！获得60积分，连续签到7天",
  "data": {
    "checkedIn": true,
    "pointsEarned": 60,
    "continuousDays": 7,
    "bonusPoints": 50,
    "checkinDate": "2024-01-15"
  }
}
```

**响应示例 - 今日已签到**:
```json
{
  "code": 200,
  "msg": "今日已签到",
  "data": {
    "checkedIn": false,
    "pointsEarned": 0,
    "continuousDays": 5,
    "bonusPoints": 0,
    "checkinDate": "2024-01-15"
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| checkedIn | Boolean | 是否签到成功 |
| pointsEarned | Integer | 本次获得的积分 |
| continuousDays | Integer | 连续签到天数 |
| bonusPoints | Integer | 额外奖励积分 |
| checkinDate | String | 签到日期（YYYY-MM-DD） |

**前端使用建议**:
- 在首页或个人中心显示签到按钮
- 根据 `continuousDays` 显示连续签到进度条
- 当 `bonusPoints > 0` 时显示特殊奖励动画
- 签到成功后更新积分显示
- 如果 `checkedIn = false`，禁用签到按钮并提示"今日已签到"

---

## 3. 商店相关接口

### 3.1 获取商店单词书列表

**接口地址**: `GET /api/store/books`

**请求头**:
```
Authorization: Bearer {token}
```

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| category | String | 否 | 分类筛选 | cet4, cet6, ielts, toefl, business, daily |
| difficulty | Integer | 否 | 难度筛选 | 1-初级, 2-中级, 3-高级 |
| sortBy | String | 否 | 排序方式 | price-价格, hot-热门, new-新品, recommend-推荐 |
| page | Integer | 否 | 页码，默认1 | 1 |
| size | Integer | 否 | 每页数量，默认20 | 20 |

**请求示例**:
```
GET /api/store/books?category=cet4&difficulty=1&sortBy=hot&page=1&size=20
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 100,
    "current": 1,
    "size": 20,
    "pages": 5,
    "records": [
      {
        "id": 1,
        "bookName": "CET-4 核心词汇",
        "description": "大学英语四级考试核心词汇，包含1500个高频单词",
        "coverImage": "/images/cet4.jpg",
        "category": "cet4",
        "difficulty": 1,
        "wordCount": 1500,
        "price": 200,
        "originalPrice": 300,
        "isHot": 1,
        "isNew": 0,
        "isRecommended": 1,
        "isPurchased": false,
        "discount": 67
      }
    ]
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 单词书ID |
| bookName | String | 单词书名称 |
| description | String | 单词书描述 |
| coverImage | String | 封面图片URL |
| category | String | 分类 |
| difficulty | Integer | 难度等级（1-3） |
| wordCount | Integer | 单词数量 |
| price | Integer | 当前价格（积分） |
| originalPrice | Integer | 原价（积分） |
| isHot | Integer | 是否热门（0-否，1-是） |
| isNew | Integer | 是否新品（0-否，1-是） |
| isRecommended | Integer | 是否推荐（0-否，1-是） |
| isPurchased | Boolean | 是否已购买 |
| discount | Double | 折扣率（百分比，如67表示67折） |

**前端使用建议**:
- 使用卡片布局展示单词书列表
- 显示封面、名称、简介、价格
- 如果 `isPurchased = true`，显示"已拥有"标签，禁用购买按钮
- 如果 `discount < 100`，显示折扣标签
- 根据 `isHot`、`isNew`、`isRecommended` 显示相应角标
- 提供筛选和排序功能

---

### 3.2 获取单词书详情

**接口地址**: `GET /api/store/books/{id}`

**请求头**:
```
Authorization: Bearer {token}
```

**请求参数**: 
- `id`: 单词书ID（路径参数）

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "bookName": "CET-4 核心词汇",
    "description": "大学英语四级考试核心词汇，包含1500个高频单词",
    "coverImage": "/images/cet4.jpg",
    "category": "cet4",
    "difficulty": 1,
    "wordCount": 1500,
    "price": 200,
    "originalPrice": 300,
    "isHot": 1,
    "isNew": 0,
    "isRecommended": 1,
    "isPurchased": false,
    "discount": 67
  }
}
```

**前端使用建议**:
- 显示完整的单词书信息
- 提供预览功能（显示部分单词示例）
- 如果未购买，显示购买按钮
- 如果已购买，显示"立即学习"按钮，跳转到单词书详情页

---

### 3.3 购买单词书

**接口地址**: `POST /api/store/books/{id}/purchase`

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数**:
- `id`: 单词书ID（路径参数）
- Body: 空或 `{}`

**响应示例 - 购买成功**:
```json
{
  "code": 200,
  "msg": "购买成功",
  "data": {
    "userBookId": 123,
    "remainingBalance": 1050
  }
}
```

**响应示例 - 积分不足**:
```json
{
  "code": 500,
  "msg": "积分不足，当前余额：150",
  "data": null
}
```

**响应示例 - 已购买**:
```json
{
  "code": 500,
  "msg": "您已拥有此单词书",
  "data": null
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| userBookId | Long | 购买后生成的用户单词书ID |
| remainingBalance | Integer | 购买后的剩余积分 |

**前端使用建议**:
- 购买前弹出确认对话框，显示价格和剩余积分
- 购买过程中显示加载动画
- 购买成功后：
  - 显示成功提示
  - 更新积分显示
  - 刷新单词书列表（更新 `isPurchased` 状态）
  - 提供"立即学习"按钮
- 购买失败时：
  - 显示失败原因
  - 如果积分不足，提供"快速获取积分"引导

---

## 4. 数据结构说明

### 4.1 分类枚举

```javascript
const CATEGORIES = {
  CET4: 'cet4',           // 大学英语四级
  CET6: 'cet6',           // 大学英语六级
  IELTS: 'ielts',         // 雅思
  TOEFL: 'toefl',         // 托福
  BUSINESS: 'business',   // 商务英语
  DAILY: 'daily'          // 日常英语
};
```

### 4.2 难度等级

```javascript
const DIFFICULTY = {
  BEGINNER: 1,    // 初级
  INTERMEDIATE: 2, // 中级
  ADVANCED: 3     // 高级
};
```

### 4.3 排序方式

```javascript
const SORT_BY = {
  PRICE: 'price',       // 价格升序
  HOT: 'hot',           // 热门
  NEW: 'new',           // 新品
  RECOMMEND: 'recommend' // 推荐
};
```

### 4.4 积分交易类型（供参考）

```javascript
const TRANSACTION_TYPE = {
  REGISTER: 1,      // 注册赠送
  CHECKIN: 2,       // 每日签到
  LEARNING: 3,      // 学习奖励
  PURCHASE: 4,      // 购买消费
  SYSTEM: 5         // 系统调整
};
```

---

## 5. 错误处理

### 5.1 常见错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | 正常处理 |
| 401 | 未授权 | 跳转到登录页 |
| 403 | 无权限 | 提示用户无权限 |
| 404 | 资源不存在 | 提示资源不存在 |
| 500 | 服务器错误 | 提示稍后重试 |

### 5.2 业务错误消息

前端应该友好地展示以下错误消息：
- "积分不足，当前余额：XXX" → 引导用户签到或学习获取积分
- "您已拥有此单词书" → 跳转到单词书学习页面
- "单词书不存在" → 返回列表页
- "网络请求失败" → 提供重试按钮

---

## 6. UI/UX 建议

### 6.1 商店首页
```
┌─────────────────────────────┐
│ 我的积分: 1250    [签到]    │
├─────────────────────────────┤
│ [筛选] 分类▼ 难度▼ 排序▼    │
├─────────────────────────────┤
│ ┌───────┐ ┌───────┐        │
│ │ 封面  │ │ 封面  │        │
│ │ CET-4 │ │ CET-6 │        │
│ │ ⭐热门│ │ ⭐新品│        │
│ │ 200积分│ │ 300积分│       │
│ │[已拥有]│ │[购买] │        │
│ └───────┘ └───────┘        │
└─────────────────────────────┘
```

### 6.2 签到弹窗
```
┌─────────────────────────────┐
│      🎉 签到成功！           │
│                             │
│   获得积分: +10             │
│   连续签到: 5天             │
│   再签2天可获得额外奖励！    │
│                             │
│   [确定]                    │
└─────────────────────────────┘
```

### 6.3 购买确认
```
┌─────────────────────────────┐
│   确认购买                   │
│                             │
│   单词书: CET-4 核心词汇     │
│   价格: 200 积分            │
│   当前余额: 1250 积分       │
│   购买后余额: 1050 积分     │
│                             │
│   [取消]  [确认购买]        │
└─────────────────────────────┘
```

---

## 7. 缓存策略建议

### 7.1 需要缓存的数据
- **商店单词书列表**: 缓存1小时（变化不频繁）
- **单词书详情**: 缓存1小时
- **用户积分余额**: 缓存5分钟，购买后立即刷新
- **签到状态**: 缓存到第二天零点

### 7.2 需要实时获取的数据
- 购买操作前的积分余额
- 签到操作前的签到状态

---

## 8. 完整调用示例（Vue.js）

```javascript
// 获取积分余额
async function getPointsBalance() {
  const res = await axios.get('/api/store/points/balance')
  if (res.code === 200) {
    this.points = res.data.balance
  }
}

// 签到
async function checkin() {
  const res = await axios.post('/api/store/checkin')
  if (res.code === 200) {
    this.$message.success(res.msg)
    this.checkinData = res.data
    // 更新积分
    await this.getPointsBalance()
  }
}

// 获取商店列表
async function getStoreBooks(params) {
  const res = await axios.get('/api/store/books', { params })
  if (res.code === 200) {
    this.bookList = res.data.records
    this.total = res.data.total
  }
}

// 购买
async function purchaseBook(bookId) {
  // 确认对话框
  const confirmed = await this.$confirm('确认购买？', '提示')
  if (!confirmed) return
  
  try {
    const res = await axios.post(`/api/store/books/${bookId}/purchase`)
    if (res.code === 200) {
      this.$message.success('购买成功')
      // 刷新积分和列表
      await this.getPointsBalance()
      await this.getStoreBooks(this.queryParams)
    }
  } catch (error) {
    this.$message.error(error.response?.data?.msg || '购买失败')
  }
}
```

---

## 9. 总结

✅ **已完成接口**:
- 获取积分余额
- 每日签到
- 查询商店单词书列表
- 获取单词书详情

🚧 **待实现接口**:
- 购买单词书（后端逻辑待完善）

💡 **优化建议**:
- 添加积分明细查询接口
- 添加购买记录查询接口
- 添加搜索功能
- 添加收藏功能

如有任何问题，请及时沟通！😊
