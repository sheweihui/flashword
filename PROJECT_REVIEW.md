# 背单词小程序项目总评

## 项目概览

**类型**：全栈背单词学习应用（微信小程序 + Spring Boot + Python Agent）
**代码量**：约 18,600 行，分布在后端 (Java) ~9,800、前端 (微信小程序) ~7,100、Python Agent ~1,600
**技术栈**：Java 17 / Spring Boot 3.2 / MyBatis / MySQL 8.0 / Redis 7 / RabbitMQ 3 / DeepSeek API / FastAPI / 微信小程序

---

## 一、系统架构（4 层）

```
微信小程序 ──HTTP──→ Spring Boot (8080) ──→ MySQL (3307) / Redis (6379) / RabbitMQ (5672)
                         │
                         └──→ Python Agent (8000) ──→ DeepSeek API
```

### 数据流

| 链路 | 说明 |
|------|------|
| 前端 → Controller → Service → Mapper → MySQL | 标准 CRUD |
| 前端 → StoreController → Redis DECR/SETNX → MySQL → MQ | 秒杀系统 |
| 前端 → AiChatController → Python Agent → DeepSeek | AI 聊天 |
| 前端 → WordController → AiWordServiceImpl → Python Agent → DeepSeek | 单词 AI 补全 |
| Redis 快速写入 → MQ 异步 → MySQL 最终写入 | 积分持久化 |

### 服务依赖

```
后端依赖: MySQL(3307), Redis(6379), RabbitMQ(5672)
Python Agent 依赖: 后端(8080), DeepSeek API
前端依赖: 后端(8080)
```

---

## 二、优势

### 1. 架构设计合理 — 中等复杂度项目中的优秀设计

- **秒杀系统设计完整**：Redis DECR 预扣库存 → SETNX 一人一单 → MySQL 乐观锁兜底 → MQ 异步最终处理。架构与生产级秒杀方案一致，只是规模不同
- **消息驱动解耦**：RabbitMQ 8 个队列 + 2 个交换机 + DLQ，手动 ACK + 重试 + 死信，比很多商业项目还规范
- **Python Agent 独立部署**：AI 能力与业务后端解耦，支持 LLM 调用、RAG、Function Calling、对话管理，架构清晰
- **Redis 多用途**：认证 Token、业务缓存、秒杀库存、幂等锁、实时计数，合理利用了不同 Redis 数据结构的特性
- **自定义注解 AOP**：`@Cacheable`、`@MethodRunTime` 等 5 个注解 + 7 个切面，代码复用度高

### 2. 基础设施完善

- **Docker Compose 一键启动**：MySQL + Redis + RabbitMQ + 后端应用，4 个服务编排，健康检查、持久化卷、网络隔离齐全
- **数据库设计规范**：15 张表，外键、索引、联合唯一键、触发器齐全，字段注释完整
- **统一响应格式**：所有接口返回 `Result<T>` 或 `PageResult`，前端统一处理
- **Token 认证拦截器**：从 Redis 验证 Token，ThreadLocal 传递用户上下文

### 3. 错误处理与可观测性

- 所有 Controller 方法有 try-catch + 日志
- 参数校验（`assertNotNull`、`throw new RuntimeException("不能为空")`）
- 秒杀使用 `SeckillMessageLog` 确保 MQ 消息幂等性
- 日志覆盖完整（入参 log.debug + 出参 log.info/error）

### 4. 代码量适中，结构清晰

- 约 18,600 行代码，后端 + 前端 + Agent 三人各司其职
- 包命名规范（controller / service / mapper / entity / config / dto / vo）
- Java 与 Python 代码风格统一（都用了 lombok / loguru 的风格）

---

## 三、不足与改进建议

### 🔴 严重问题（建议优先修复）

| 问题 | 说明 | 建议 |
|------|------|------|
| **测试几乎为零** | 仅 1 个 `SeckillIdempotencyTest.java`，且引用了已删除的 `SeckillService`，编译会失败 | `SeckillIdempotencyTest.java` 立即删除或重写。关键业务（用户注册、登录、签到、购买）至少加单元测试 |
| **API Key 硬编码在 YML** | `application.yml` 中 `ai.api-key: sk-43bef359095e4fc7b35d43a35cfca5b5` 是明文硬编码 | 应通过环境变量注入，YML 只保留占位符 `${AI_API_KEY}` |
| **请求体接收方式不统一** | 有的用 `@RequestBody Map<String, String>`，有的用 DTO，有的直接 `@PathVariable` | 统一使用 DTO 类 |
| **无版本控制策略** | API 无版本号（如 `/api/v1/`），前端强行依赖最新后端 | 加 `/api/v1/` 前缀兼容 |

### 🟡 中等问题（建议后续改进）

| 问题 | 说明 | 建议 |
|------|------|------|
| **全局异常处理器不完善** | `DatabaseExceptionHandler` 存在但只处理数据库异常。Controller 中仍有大量 try-catch 样板代码 | 扩展为统一异常处理器，处理 `RuntimeException`、`MethodArgumentNotValidException` 等，消除 Controller 中的 try-catch |
| **Service 层缺少接口隔离** | 所有 Service 方法都暴露在一个接口中 → `impl` 中 | 按业务领域拆分接口（如 `SeckillService` 从 `StoreService` 中拆分出），避免臃肿 |
| **Python Agent 无测试** | 0 个测试文件 | 至少加 endpoint 集成测试，验证 /agent/chat 和 /agent/word/enrich 返回格式 |
| **前端硬编码 API 地址** | `api.js` 中 `const API_BASE_URL = 'http://localhost:8080/api'` 硬编码 | 通过环境变量或构建参数配置 |
| **缓存穿透防护** | Redis 缓存无空值缓存，不存在的 key 每次都会击穿到 DB | 对不存在的数据也缓存空值（短 TTL），或用布隆过滤器 |
| **日志中携带用户 Token** | TokenInterceptor 日志可能输出 Token | 确保日志脱敏，特别是 `Authorization` 头 |

### 🟢 轻微问题（有空再改）

| 问题 | 说明 | 建议 |
|------|------|------|
| **多处魔法字符串** | Redis key 散落在代码中（如 `"user:books:"`、`"store:sales:"`） | 抽取到 Constants 类中集中管理 |
| **Lombok 过度使用** | `@Data` 在 entity/vo/dto 上，`equals`/`hashCode` 在 JPA 实体上可能有问题 | entity 层谨慎使用 `@Data`，考虑 `@Getter/@Setter` 代替 |
| **批量 AI 查询固定 1s 延迟** | `Thread.sleep(1000)` 写死在循环中 | 用指数退避或干脆并行请求 |
| **前端代码风格不一致** | 部分页面用 callback，部分用 Promise，部分混用 | 统一为 async/await |
| **无配置中心** | 配置全在 `application.yml` 中 | 规模增长后考虑 Nacos 或 Spring Cloud Config |
| **无 CI/CD** | 无 GitHub Actions 或 Jenkins 配置 | 加 CI 流水线自动构建 + 测试 |
| **无 API 文档** | 无 Swagger/SpringDoc 集成 | 加 `springdoc-openapi-starter-webmvc-ui` 自动生成文档 |

---

## 四、技术选型评估

| 选型 | 评价 |
|------|------|
| **Spring Boot 3.2 + Java 17** | ✅ 主流技术，LTS 版本，生态成熟 |
| **MyBatis（非 JPA）** | ✅ 适合复杂 SQL，学习曲线低，项目规模不大无需 JPA 的自动建表 |
| **MySQL 8.0** | ✅ 稳定可靠，够用 |
| **Redis 7** | ✅ 部署方式合理，用途多样化 |
| **RabbitMQ** | ✅ 相比 Kafka 更适合这个规模，功能足够 |
| **DeepSeek API** | ✅ 性价比高，OpenAI 兼容 |
| **Python Agent (FastAPI)** | ✅ 独立部署解耦，功能完善（RAG + FC），但缺少测试 |
| **微信小程序** | ✅ 匹配背单词场景，但只能跑在微信里 |
| **Docker Compose** | ✅ 开发/部署体验好 |
| **Lombok** | ⚠️ 方便但 entity 层小心使用 |
| **Hutool** | ✅ 实用的工具类库 |
| **无单元测试框架** | ❌ **最大短板** |

---

## 五、亮点功能

### 1. 秒杀系统（完整度 ⭐⭐⭐⭐⭐）
```
请求 → Redis DECR（预扣库存）
     → SETNX（一人一单，防重复）
     → 创建 SeckillOrder
     → MySQL 乐观锁（update stock where stock >= 0）
     → MQ 异步（积分扣减 + 单词复制）
     → SeckillMessageLog 幂等追踪
```
具备完整的生产级秒杀要素：预扣 + 限购 + 乐观锁 + 异步 + 幂等，只是流量规模不同。

### 2. AI Agent 集成（完整度 ⭐⭐⭐⭐）
- RAG 检索上下文（查词 + 用户画像）
- Function Calling 调用后端 API（查单词、查询积分、签到、购买秒杀）
- 对话持久化 + 摘要压缩
- 服务降级（无 LLM 时用本地规则回复）
- 双通道支持（前端直连 / Agent 转发）

### 3. 消息驱动（完整度 ⭐⭐⭐⭐）
- 8 个队列 × 2 个交换机 × DLQ
- 手动 ACK + 3 次重试 + 死信
- 秒杀消息幂等性追踪

---

## 六、简历亮点提炼

如果用于实习求职简历，以下是最有价值的技术点：

1. **秒杀系统设计**：Redis 预扣库存 + SETNX 一人一单 + MySQL 乐观锁 + MQ 异步最终处理
2. **订单状态追踪 + 补偿机制**：订单带 status 字段（处理中/已完成/异常），定时任务扫描超时未完成订单自动重试 MQ
3. **双层接口限流**：Redis Lua 令牌桶（全局限流）+ 固定窗口（用户级限流），在 Redis 操作前拦截恶意请求
4. **RabbitMQ 消息驱动**：8 队列 DLQ 架构，手动 ACK 重试机制，消息幂等性设计 + 本地消息表保证可靠投递
5. **多级缓存策略**：Redis 实时计数 → MQ 异步持久化 → MySQL 最终一致性
6. **Docker Compose 编排**：4 服务容器化部署
7. **全栈能力**：Java Spring Boot + Python FastAPI + 微信小程序

---

## 七、总结

**一句话评价**：这是一个架构设计远超一般课程项目的全栈背单词应用。

**最突出**：秒杀系统、消息队列、AI Agent 集成都体现了接近生产级的思考，适合拿来面试展示。

**最大短板**：零测试覆盖，编译通过的测试还是 dead code。JD/面试官问"你们怎么保证代码质量"时，会说不出话。

**建议路线**：修测试 → 修 API Key 硬编码 → 加全局异常处理器 → 加 CI → 日常修小问题。
