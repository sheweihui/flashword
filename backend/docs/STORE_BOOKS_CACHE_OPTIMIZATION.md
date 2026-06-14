# 商店单词书列表缓存优化

## 📋 更新内容

### 1. 新增文件
- `CacheableAspect.java` - 缓存读取切面，实现先查缓存、缓存未命中再查数据库的逻辑

### 2. 修改文件
- `CacheEvictAspect.java` - 完善缓存清除功能，支持SpEL表达式解析
- `StoreServiceImpl.java` - 为 `queryStoreBooks` 方法添加 `@Cacheable` 注解
- `StoreController.java` - 优化日志输出，更清晰地显示请求信息

## 🎯 缓存策略

### 缓存Key格式
```
store:books:{page}:{size}:{category}:{difficulty}
```

示例：
- `store:books:1:10:all:all` - 第1页，每页10条，全部分类，全部难度
- `store:books:1:10:cet4:2` - 第1页，每页10条，CET-4分类，中级难度

### 缓存过期时间
- **1800秒（30分钟）** - 商店单词书列表数据相对稳定，设置30分钟过期时间

## 📊 工作流程

### 第一次查询（缓存未命中）
```
用户请求 → Controller → Service (@Cacheable) 
    → 检查Redis缓存（未命中）
    → 查询数据库
    → 写入Redis缓存
    → 返回结果
```

**日志输出：**
```
📚 [REQUEST] GET /api/store/books | 用户: 7
🔍 [Redis] 尝试从缓存获取数据 | Key: store:books:1:10:all:all
💾 [DB] 缓存未命中，从数据库查询 | Key: store:books:1:10:all:all
✅ [DB→Redis] 数据已缓存 | Key: store:books:1:10:all:all | 过期时间: 1800秒
```

### 第二次查询（缓存命中）
```
用户请求 → Controller → Service (@Cacheable) 
    → 检查Redis缓存（命中）
    → 直接返回缓存数据
```

**日志输出：**
```
📚 [REQUEST] GET /api/store/books | 用户: 7
🔍 [Redis] 尝试从缓存获取数据 | Key: store:books:1:10:all:all
✅ [Redis] 缓存命中 | Key: store:books:1:10:all:all
```

## 🔧 技术实现

### 1. CacheableAspect（缓存读取切面）
- 拦截带有 `@Cacheable` 注解的方法
- 使用SpEL表达式动态生成缓存Key
- 实现"先查缓存，缓存未命中再执行方法并写入缓存"的逻辑

### 2. CacheEvictAspect（缓存清除切面）
- 拦截带有 `@CacheEvict` 注解的方法
- 在方法执行后清除指定的缓存
- 支持SpEL表达式解析和批量清除

### 3. SpEL表达式支持
支持在注解中使用SpEL表达式引用方法参数：
```java
@Cacheable(key = "'store:books:' + #queryDTO.page + ':' + #queryDTO.size", expire = 1800)
```

## 📝 使用示例

### 在其他Service方法中添加缓存

```java
@Service
public class YourServiceImpl implements YourService {
    
    // 添加缓存，过期时间1小时
    @Override
    @Cacheable(key = "'your:key:' + #userId", expire = 3600)
    public Object getData(Long userId) {
        // 数据库查询逻辑
    }
    
    // 更新数据时清除缓存
    @Override
    @CacheEvict(key = "'your:key:' + #userId")
    public void updateData(Long userId, Object data) {
        // 更新逻辑
    }
}
```

## ⚠️ 注意事项

### 1. 缓存一致性
当商店单词书数据发生变化时（如新增商品、修改价格等），需要清除相关缓存。

**示例：在商品管理Service中**
```java
@CacheEvict(key = "'store:books:' + #page + ':' + #size + ':all:all'", allEntries = true)
public void addNewProduct(StoreProduct product) {
    // 添加商品逻辑
}
```

### 2. 缓存Key设计原则
- **唯一性**：确保不同参数的请求有不同的缓存Key
- **可读性**：Key格式清晰，便于调试和管理
- **合理性**：根据数据变化频率设置合适的过期时间

### 3. 性能考虑
- 缓存适合读多写少的场景
- 对于频繁变化的数据，缩短缓存过期时间或不使用缓存
- 监控缓存命中率，优化缓存策略

## 🚀 下一步优化建议

1. **添加缓存统计**：记录缓存命中率、平均响应时间等指标
2. **缓存预热**：在系统启动时预加载热门数据到缓存
3. **分级缓存**：结合本地缓存（Caffeine）和分布式缓存（Redis）
4. **缓存穿透保护**：使用布隆过滤器或空值缓存防止缓存穿透
5. **缓存雪崩防护**：为不同Key设置随机过期时间

## 📈 预期效果

- **首次查询**：约 100-200ms（数据库查询）
- **缓存命中**：约 5-10ms（Redis查询）
- **性能提升**：约 **10-20倍**

## ✅ 测试验证

重启应用后，连续两次访问 `/api/store/books`，观察日志输出：

**第一次访问：**
```
📚 [REQUEST] GET /api/store/books | 用户: 7
💾 [DB] 缓存未命中，从数据库查询 | Key: store:books:1:10:all:all
✅ [DB→Redis] 数据已缓存
```

**第二次访问：**
```
📚 [REQUEST] GET /api/store/books | 用户: 7
✅ [Redis] 缓存命中 | Key: store:books:1:10:all:all
```

如果看到 "缓存命中" 的日志，说明缓存功能正常工作！
