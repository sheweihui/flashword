# 日志配置修复和异步缓存功能实现

## 1. 日志配置修复

### 问题描述
之前很多 `log.info` 消息不显示，原因是 `application.yml` 中的日志级别配置被注释掉了。

### 解决方案
在 `src/main/resources/application.yml` 中启用了以下日志配置：

```yaml
logging:
  level:
    root: ERROR
    # 只打印你自己项目的 INFO 日志（log.info）
    org.example: INFO
    # 彻底屏蔽MyBatis所有SQL、行数据、会话、连接日志
    org.apache.ibatis: OFF
    org.apache.ibatis.session: OFF
    org.apache.ibatis.logging: OFF
    org.apache.ibatis.session.defaults: OFF
    # 屏蔽Mapper接口的SQL日志
    # 屏蔽JDBC相关日志
    org.springframework.jdbc: OFF
    org.mybatis: OFF
    # 屏蔽连接池
    com.zaxxer.hikari: OFF
    # 屏蔽Spring框架冗余日志
    org.springframework: ERROR
```

这样设置后，只有 `org.example` 包下的 INFO 级别日志会显示，其他框架的日志都被屏蔽或设为 ERROR 级别。

## 2. 登录时异步缓存功能实现

### 功能描述
当用户登录时，系统会通过 RabbitMQ 异步地将用户的商店、单词本、单词信息缓存到 Redis 中，以提高后续访问速度。

### 实现细节

#### 2.1 消息生产者 (`UserMessageProducer`)
- 修改了 `sendUserLoginMessage` 方法，发送包含用户ID和token的消息
- 添加了时间戳字段用于追踪
- 增加了日志输出

#### 2.2 消息消费者 (`UserMessageConsumer`)
- 实现了完整的异步缓存逻辑：
  1. 缓存用户的单词本列表
  2. 缓存每个单词本中的单词
  3. 预留了商店单词书缓存接口
- 使用统一的 Redis key 前缀：`user:cache:`
- 缓存过期时间设置为 24 小时
- 添加了详细的日志记录和异常处理

#### 2.3 登录控制器更新 (`UserController`)
- 增强了登录过程中的日志输出
- 在登录成功后调用异步缓存功能
- 添加了更详细的成功/失败日志

#### 2.4 Redis工具类增强 (`RedisUtil`)
- 添加了泛型方法 `<T> T get(String key, Class<T> clazz)` 支持类型安全的数据获取

#### 2.5 测试控制器 (`CacheTestController`)
- 创建了专门的测试接口来验证缓存功能
- 提供查询和清除用户缓存的API

## 3. 缓存数据结构

### Redis Key 命名规范
- 用户单词本列表：`user:cache:vocab_books:{userId}`
- 单词本中的单词：`user:cache:words:book_{bookId}`

### 缓存内容
- 单词本列表：`List<UserVocabularyBook>`
- 单词列表：`List<UserWord>`

## 4. 使用说明

### 启动服务
确保以下服务正在运行：
- MySQL 数据库
- Redis 服务器
- RabbitMQ 服务器

### 测试流程
1. 用户登录：`POST /api/user/login`
2. 检查日志输出，确认异步缓存开始执行
3. 等待几秒让异步处理完成
4. 测试缓存：`GET /api/cache/user/{userId}`
5. 清除缓存：`DELETE /api/cache/user/{userId}`

### 日志示例
```
🔐 [用户登录] 开始登录 | 用户名: testuser
✅ [用户登录] 登录成功 | 用户ID: 1 | 用户名: testuser | Token: abc123...
📤 [UserMessageProducer] 发送用户登录消息 | 用户ID: 1 | Token: abc123...
📥 [UserMessageConsumer] 收到用户登录消息: {userId=1, token=abc123..., timestamp=...}
🔄 [UserMessageConsumer] 开始异步缓存用户数据 | 用户ID: 1
📚 [UserMessageConsumer] 缓存单词本列表 | 用户ID: 1 | 数量: 3
📝 [UserMessageConsumer] 缓存单词本单词 | 单词本ID: 10 | 单词数量: 50
📝 [UserMessageConsumer] 缓存单词本单词 | 单词本ID: 11 | 单词数量: 30
✅ [UserMessageConsumer] 用户数据缓存完成 | 用户ID: 1 | Token: abc123...
```

## 5. 注意事项

1. 异步缓存不会阻塞登录响应，用户体验不受影响
2. 缓存失败不会影响登录成功，只是缺少预缓存
3. 所有缓存操作都有详细的日志记录便于调试
4. 缓存有过期时间，避免数据长期不一致
5. 可以根据业务需求扩展商店数据的缓存逻辑
