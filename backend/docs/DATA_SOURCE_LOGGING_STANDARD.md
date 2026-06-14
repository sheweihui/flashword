# 数据来源标识规范

## 📋 概述

为了清晰地区分数据是从数据库（DB）还是 Redis 缓存获取的，我们在所有数据查询操作中添加了明确的数据来源标识。

## 🎯 标识规范

### 1. 日志前缀标识

| 标识 | 含义 | 使用场景 |
|------|------|----------|
| `💾 [DB]` | 从数据库查询数据 | 所有 Mapper/Service 层的数据库查询操作 |
| `✅ [DB]` | 数据库查询成功 | 数据库操作完成后的成功日志 |
| `❌ [DB]` | 数据库查询失败 | 数据库操作失败的错误日志 |
| `🔍 [Redis]` | 尝试从 Redis 获取数据 | Controller/Service 层尝试读取缓存 |
| `✅ [Redis]` | Redis 缓存命中 | 成功从缓存获取数据 |
| `❌ [Redis]` | Redis 缓存未命中或失败 | 缓存不存在或读取失败 |
| `✅ [DB→Redis]` | 从数据库获取并缓存到 Redis | 缓存穿透时的数据加载和缓存操作 |

### 2. 日志级别建议

- **INFO**: 重要的业务流程节点（如登录、购买、缓存命中/未命中）
- **DEBUG**: 详细的数据查询操作（如每次数据库查询、缓存检查）
- **TRACE**: 非常详细的调试信息（如循环中的单次查询）
- **WARN**: 警告信息（如积分不足、重复购买）
- **ERROR**: 错误信息（如查询失败、缓存失败）

## 📝 实施示例

### 1. Consumer 层 - 异步缓存

**文件**: `UserMessageConsumer.java`

```java
private void cacheUserVocabularyBooks(Long userId) {
    try {
        // 标识：从数据库查询
        log.info("💾 [DB→Redis] 从数据库查询用户单词本列表 | 用户ID: {}", userId);
        List<UserVocabularyBook> books = userVocabularyBookService.listByUserId(userId);
        String cacheKey = REDIS_KEY_PREFIX + "vocab_books:" + userId;
        
        // 标识：缓存到 Redis
        redisUtil.set(cacheKey, books, CACHE_EXPIRE_TIME, TimeUnit.HOURS);
        log.info("✅ [DB→Redis] 单词本列表已缓存到Redis | 用户ID: {} | 数量: {} | Key: {}", 
                userId, books.size(), cacheKey);
        cacheSuccessCount++;
    } catch (Exception e) {
        log.error("❌ [DB→Redis] 缓存单词本列表失败 | 用户ID: {}", userId, e);
        cacheFailCount++;
    }
}
```

### 2. Service 层 - 数据库查询

**文件**: `UserVocabularyBookServiceImpl.java`

```java
@Override
public List<UserVocabularyBook> listByUserId(Long userId) {
    // 标识：从数据库查询
    log.debug("💾 [DB] 从数据库查询用户单词本列表 | 用户ID: {}", userId);
    return userVocabularyBookMapper.selectByUserId(userId);
}

@Override
public List<UserWord> getBookByIdGetALLWORD(Long bookId) {
    // 标识：从数据库查询关联关系
    log.debug("💾 [DB] 从数据库查询单词书-单词关联 | 单词书ID: {}", bookId);
    List<UserBookWord> bookWords = userBookWordMapper.selectByBookId(bookId);
    
    // 获取单词详情
    List<UserWord> words = new ArrayList<>();
    for (UserBookWord bookWord : bookWords) {
        // 标识：循环中的单次查询（使用 TRACE 级别）
        log.trace("💾 [DB] 从数据库查询单词详情 | 单词ID: {}", bookWord.getWordId());
        UserWord word = userWordMapper.selectById(bookWord.getWordId());
        if (word != null) {
            words.add(word);
        }
    }
    
    log.debug("✅ [DB] 从数据库获取单词书单词列表完成 | 单词书ID: {} | 单词数: {}", bookId, words.size());
    return words;
}
```

### 3. Controller 层 - 缓存优先策略

**文件**: `VocabularyBookController.java`

```java
@GetMapping("/words")
public Result<List<UserWord>> getBookByIdAllWord(@RequestParam("bookId") Long bookId) {
    try {
        String redisKey = "user:" + UserContextHolder.getUserId() + ":word:" + bookId + ":words";
        
        // 标识：尝试从 Redis 获取
        log.debug("🔍 [Redis] 尝试从 Redis 获取单词数据 | Key: {}", redisKey);
        List<UserWord> words = (List<UserWord>) redisUtil.get(redisKey);
        
        if (words != null) {
            redisUtil.expire(redisKey, 1, TimeUnit.DAYS);
            // 标识：缓存命中
            log.info("✅ [Redis] 缓存命中 | 单词数: {} | Key: {}", words.size(), redisKey);
            return Result.success(words);
        }
        
        // 标识：缓存未命中，从数据库查询
        log.info("💾 [DB] Redis 缓存未命中，从数据库查询 | 单词书ID: {}", bookId);
        List<UserWord> words1 = userVocabularyBookService.getBookByIdGetALLWORD(bookId);
        redisUtil.set(redisKey, words1, 1, TimeUnit.DAYS);
        
        // 标识：从数据库获取并缓存
        log.info("✅ [DB→Redis] 从数据库获取并缓存到 Redis | 单词书ID: {} | 单词数: {} | Key: {}", 
                bookId, words1 != null ? words1.size() : 0, redisKey);
        return Result.success(words1);
    } catch (Exception e) {
        log.error("❌ 查询单词书单词列表失败", e);
        return Result.error("查询失败: " + e.getMessage());
    }
}
```

### 4. Service 层 - 混合数据源

**文件**: `StoreServiceImpl.java`

```java
@Override
@Transactional
public Long purchaseBook(Long userId, Long storeBookId) {
    log.info("🛒 [购买] 开始处理 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
    
    // 1. 检查是否已购买（数据库）
    if (isPurchased(userId, storeBookId)) {
        log.warn("⚠️ [购买] 用户已购买 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
        throw new RuntimeException("您已经购买过该单词书，无需重复购买");
    }
    
    // 2. 获取商品信息（数据库）
    log.debug("💾 [DB] 从数据库查询商品信息 | 商店书ID: {}", storeBookId);
    StoreProduct product = storeProductMapper.selectById(storeBookId);
    
    // 3. 检查积分余额（Redis）
    String key = "user:points:" + userId;
    log.debug("🔍 [Redis] 从 Redis 获取用户积分余额 | Key: {}", key);
    Object balanceObj = redisUtil.get(key);
    
    long balance = ((Number) balanceObj).longValue();
    log.debug("✅ [Redis] 获取用户积分余额成功 | 余额: {}", balance);
    
    // ... 其他逻辑
}
```

## 📊 修改的文件清单

### 1. Consumer 层
- ✅ `UserMessageConsumer.java` - 异步缓存消费者
  - 添加 `[DB→Redis]` 标识
  - 详细记录缓存过程

### 2. Service 层
- ✅ `UserVocabularyBookServiceImpl.java`
  - `listByUserId()` - 添加 `[DB]` 标识
  - `getBookByIdGetALLWORD()` - 添加 `[DB]` 标识
  
- ✅ `StoreServiceImpl.java`
  - `queryBookWords()` - 添加 `[DB]` 标识
  - `isPurchased()` - 添加 `[DB]` 标识
  - `purchaseBook()` - 添加 `[DB]` 和 `[Redis]` 标识

### 3. Controller 层
- ✅ `VocabularyBookController.java`
  - `getBooksByUserId()` - 添加 `[DB]` 标识
  - `getBookByIdAllWord()` - 添加 `[Redis]` 和 `[DB→Redis]` 标识
  
- ✅ `StoreController.java`
  - `queryStoreBooks()` - 添加 `[DB]` 标识
  - `queryBookWords()` - 添加 `[DB]` 标识

## 🔍 日志输出示例

### 场景1：用户登录异步缓存

```
🔐 [用户登录] 开始登录 | 用户名: testuser
✅ [用户登录] 登录成功 | 用户ID: 1 | 用户名: testuser | Token: abc123...
📤 [UserMessageProducer] 发送用户登录消息 | 用户ID: 1 | Token: abc123...
📥 [UserMessageConsumer] 收到用户登录消息: {userId=1, token=abc123..., timestamp=...}
🔄 [UserMessageConsumer] 开始异步缓存用户数据 | 用户ID: 1
💾 [DB→Redis] 从数据库查询用户单词本列表 | 用户ID: 1
💾 [DB] 从数据库查询用户单词本列表 | 用户ID: 1
✅ [DB→Redis] 单词本列表已缓存到Redis | 用户ID: 1 | 数量: 3 | Key: user:cache:vocab_books:1
💾 [DB→Redis] 从数据库查询用户单词本列表（用于获取单词） | 用户ID: 1
💾 [DB] 从数据库查询用户单词本列表 | 用户ID: 1
💾 [DB→Redis] 从数据库查询单词本单词 | 单词本ID: 10
💾 [DB] 从数据库查询单词书-单词关联 | 单词书ID: 10
✅ [DB→Redis] 单词数据已缓存到Redis | 单词本ID: 10 | 单词数量: 50 | Key: user:cache:words:book_10
✅ [UserMessageConsumer] 用户数据缓存完成 | 用户ID: 1 | Token: abc123...
📊 [UserMessageConsumer] 缓存统计 | 成功: 4 | 失败: 0 | 总计: 4
```

### 场景2：查询单词书单词（缓存命中）

```
🔍 [Redis] 尝试从 Redis 获取单词数据 | Key: user:1:word:10:words
✅ [Redis] 缓存命中 | 单词数: 50 | Key: user:1:word:10:words
```

### 场景3：查询单词书单词（缓存未命中）

```
🔍 [Redis] 尝试从 Redis 获取单词数据 | Key: user:1:word:10:words
💾 [DB] Redis 缓存未命中，从数据库查询 | 单词书ID: 10
💾 [DB] 从数据库查询单词书-单词关联 | 单词书ID: 10
✅ [DB] 从数据库获取单词书单词列表完成 | 单词书ID: 10 | 单词数: 50
✅ [DB→Redis] 从数据库获取并缓存到 Redis | 单词书ID: 10 | 单词数: 50 | Key: user:1:word:10:words
```

### 场景4：购买流程（混合数据源）

```
🛒 [购买] 开始处理 | 用户ID: 1 | 商店书ID: 5
💾 [DB] 从数据库检查用户是否已购买 | 用户ID: 1 | 商品ID: 5
💾 [DB] 从数据库查询商品信息 | 商店书ID: 5
🔍 [Redis] 从 Redis 获取用户积分余额 | Key: user:points:1
✅ [Redis] 获取用户积分余额成功 | 余额: 1000
💰 [购买] 扣除积分 | 用户ID: 1 | 扣除: 100 | 剩余: 900
💾 [DB] 准备创建用户单词书到数据库
📚 [购买] 创建用户单词书 | 用户书ID: 15
💾 [DB] 从数据库查询公共单词书的单词关联 | 商店书ID: 5
📋 [购买] 复制单词完成 | 用户书ID: 15 | 单词数量: 50
✅ [购买] 购买完成 | 用户ID: 1 | 商店书ID: 5 | 用户书ID: 15
```

## 💡 最佳实践

### 1. 统一标识格式
```
[图标] [数据源] 操作描述 | 关键参数
```

### 2. 关键信息必须包含
- 用户ID（涉及用户数据时）
- 资源ID（单词书ID、单词ID等）
- 数据量（列表大小、数量等）
- 缓存Key（Redis 操作时）

### 3. 日志级别选择
- **INFO**: 业务流程关键节点、缓存命中/未命中
- **DEBUG**: 每次数据查询操作
- **TRACE**: 循环内部的详细操作

### 4. 异常处理
- 所有异常日志都要包含 `[数据源]` 标识
- 使用 `❌` 图标表示失败
- 记录足够的上下文信息便于排查

## 🎯 优势

1. **清晰的数据流向** - 一眼就能看出数据来自哪里
2. **便于性能分析** - 快速识别缓存命中率和数据库压力
3. **简化问题排查** - 清楚知道是哪个数据源出了问题
4. **统一的日志风格** - 提高代码可读性和可维护性
5. **支持监控告警** - 可以基于标识进行日志分析和告警

## 📌 注意事项

1. **保持一致性** - 所有新代码都应遵循此规范
2. **不要过度日志** - DEBUG 级别在 production 环境应关闭
3. **敏感信息脱敏** - 不要在日志中记录密码等敏感信息
4. **性能考虑** - 避免在高频循环中记录过多日志
5. **定期审查** - 定期检查日志输出，优化不必要的日志
