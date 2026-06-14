# 面试八股文整理 — 背单词小程序项目

> 结合本项目技术栈，梳理 Java 后端面试常见问题，每个问题附「项目里怎么用的」作为实战经验。

---

## 一、Java 基础

### 1. HashMap 底层原理？线程安全吗？

**八股答案：**
- 数组 + 链表 + 红黑树（JDK 8+）
- put 流程：hash(key) → 定位桶 → 若空则直接插入 → 若冲突则尾插法挂链表 → 链表长度 ≥8 且总容量 ≥64 转红黑树
- 扩容：默认 16，负载因子 0.75，扩容为 2 倍，rehash 时元素要么在原位置，要么在原位置 + oldCap

**项目里：**
```java
// AiChatController 之前用 ConcurrentHashMap 管理会话
private final ConcurrentHashMap<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();
```
HashMap 线程不安全 → 高并发下 put 可能死循环（JDK 7 头插法）→ 用 ConcurrentHashMap 分段锁/CAS 保证线程安全。

### 2. ConcurrentHashMap 怎么保证线程安全？

**八股答案：**
- JDK 7：Segment 分段锁，默认 16 个 Segment，继承 ReentrantLock
- JDK 8：CAS + synchronized，只锁桶的头节点，粒度更细
- size() 先无锁统计，不一致时加锁重试

**项目里：**
```java
// UserContextHolder 用 ThreadLocal 存用户上下文
private static final ThreadLocal<UserContext> USER_CONTEXT = new ThreadLocal<>();

// 秒杀用 SETNX 做分布式锁
Boolean locked = redisUtil.setIfAbsent("seckill:user:" + userId + ":" + activityId, "1", 1, TimeUnit.HOURS);
```
ConcurrentHashMap 解决单机并发，分布式场景还是得靠 Redis SETNX。

### 3. ThreadLocal 原理？内存泄漏？

**八股答案：**
- 每个 Thread 持有 ThreadLocalMap，Entry 的 key 是弱引用 ThreadLocal，value 是强引用
- 内存泄漏：key 被 GC 回收后，value 永远无法访问 → 用完后调用 `remove()`

**项目里：**
```java
public class UserContextHolder {
    private static final ThreadLocal<UserContext> USER_CONTEXT = new ThreadLocal<>();
    
    public static void setUserContext(UserContext context) { USER_CONTEXT.set(context); }
    public static Long getUserId() { return USER_CONTEXT.get().getUserId(); }
    public static void clear() { USER_CONTEXT.remove(); }
}

// Interceptor 中
@Override
public boolean preHandle(...) {
    UserContextHolder.setUserContext(userContext);
    return true;
}
@Override
public void afterCompletion(...) {
    UserContextHolder.clear();  // 必须 remove，否则内存泄漏！
}
```
每个 HTTP 请求一个线程，Interceptor 的 `afterCompletion` 里 `remove()` 是标准写法。

---

## 二、Spring Boot

### 4. Spring Boot 自动配置原理？

**八股答案：**
- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 加载 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 条件注解：`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 控制是否生效

**项目里：**
```yaml
# 配置文件中 ai.enabled=true/false 控制是否启用 AI 功能
ai:
  enabled: true
```
可以进一步配合 `@ConditionalOnProperty("ai.enabled")` 让整个 AI 模块开关。

### 5. Spring 事务传播行为？

**八股答案：**
- `REQUIRED`（默认）：有事务用当前事务，没有则新建
- `REQUIRES_NEW`：不管有没有，都挂起当前事务新建
- `NESTED`：嵌套事务，回滚到保存点
- `MANDATORY`、`SUPPORTS`、`NOT_SUPPORTED`、`NEVER` 等

**项目里：**
```java
@Override
@Transactional  // 默认 REQUIRED
public UserWord enrichAndSaveUserWord(String wordText) {
    AiWordInfo wordInfo = callAiApi(wordText);
    // ... 保存 UserWord
    return userWord;
}
```
注意：`@Transactional` 默认只回滚 `RuntimeException`，`Exception` 不回滚，需要显式 `rollbackFor = Exception.class`。

### 6. Spring AOP 原理？

**八股答案：**
- 基于动态代理：有接口用 JDK Proxy，没接口用 CGLIB
- 切面执行顺序：`@Around` → `@Before` → 方法执行 → `@AfterReturning`/`@AfterThrowing` → `@After` → `@Around` 结束

**项目里：**
```java
@Aspect
@Component
public class LogAspect {
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || ...")
    public Object around(ProceedingJoinPoint joinPoint) {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();  // 触发目标方法
        long elapsed = System.currentTimeMillis() - start;
        log.info("{} | {}ms", methodName, elapsed);
    }
}
```
项目用了 7 个切面覆盖缓存、耗时、日志、MQ 追踪，面试可以说自己对 AOP 理解很深。

---

## 三、MySQL

### 7. 索引失效场景？

**八股答案：**
- 最左前缀不满足
- 对索引列使用函数/运算 `WHERE SUBSTR(name,1,3) = 'abc'`
- 隐式类型转换 `WHERE phone = 123`（phone 是 varchar）
- `LIKE '%xxx'` 以 % 开头
- OR 条件中有非索引列
- 数据分布导致优化器认为全表扫描更快

**项目里：**
```sql
-- 表中索引
UNIQUE INDEX `uk_book_word`(`book_id`, `word_id`)  -- 联合唯一
INDEX `idx_book`(`book_id`)
INDEX `idx_category`(`category`)
INDEX `idx_difficulty`(`difficulty`)
```
`uk_book_word(book_id, word_id)` 是联合索引，按最左前缀原则，`WHERE word_id = ?` 会失效。

### 8. 事务隔离级别？

**八股答案：**
- READ UNCOMMITTED：脏读
- READ COMMITTED（MySQL 默认？不，是 REPEATABLE READ）：不可重复读
- REPEATABLE READ（MySQL 默认）：幻读（InnoDB 通过 MVCC + Gap Lock 解决）
- SERIALIZABLE：性能最差

**项目里：**
```sql
-- 秒杀扣库存的乐观锁
UPDATE seckill_activity SET stock = stock - 1, version = version + 1
WHERE id = ? AND stock > 0 AND version = ?
```
用乐观锁而非悲观锁（行锁），在 REPEATABLE READ 下不会产生间隙锁问题，并发性能更高。

### 9. MVCC 原理？

**八股答案：**
- 每行记录有两个隐藏列：`trx_id`（最近修改事务ID）、`roll_pointer`（回滚指针指向 Undo Log）
- ReadView 包含：`creator_trx_id`、`low_limit_id`、`up_limit_id`、`m_ids`（活跃事务列表）
- 判断规则：
  - `trx_id < up_limit_id` → 可见
  - `trx_id >= low_limit_id` → 不可见
  - `trx_id` 在 `m_ids` 中 → 不可见（未提交）

**项目里：**
秒杀读库存时不用加 `SELECT ... FOR UPDATE`，因为 MVCC 的快照读就能满足需求，写时才用乐观锁。这是很多面试官会问的"读的时候加锁吗"的最佳答案。

### 10. 分页查询优化？

**八股答案：**
- `LIMIT 100000, 20` 需要扫描前面所有行 → 用子查询或覆盖索引优化
- 方法一：`WHERE id > 100000 LIMIT 20`
- 方法二：`SELECT * FROM t WHERE id IN (SELECT id FROM t WHERE ... LIMIT 100000, 20)` — MySQL 不支持子查询 LIMIT
- 方法三：`SELECT * FROM t JOIN (SELECT id FROM t WHERE ... ORDER BY id LIMIT 100000, 20) AS tmp ON t.id = tmp.id`

**项目里：**
```java
// 分页查询商店列表
PageResult<StoreBookVO> queryStoreBooks(StoreBookQueryDTO queryDTO)
```
当前项目数据量小（几千条）所以没做深度分页优化，但如果面试官问就说知道怎么优化。

---

## 四、Redis

### 11. Redis 数据结构及使用场景？

**八股答案：**
| 结构 | 底层 | 项目用途 |
|------|------|----------|
| String | SDS（动态字符串） | Token、积分余额、销售计数 |
| Hash | 压缩列表/哈希表 | 用户缓存信息 |
| List | 快速列表 | 消息队列（但项目用 RabbitMQ） |
| Set | 整数集合/哈希表 | — |
| Sorted Set | 跳表 | 排行榜 |
| **SETNX** | String 的特殊用法 | 分布式锁、秒杀一人一单 |

**项目里：**
```java
// String — 原子增减（销售计数）
redisUtil.increment("store:sales:" + productId, 1);

// String — 缓存
redisUtil.set("user:books:" + userId, books);

// SETNX — 分布式锁/幂等
redisUtil.setIfAbsent("seckill:user:" + userId + ":" + activityId, "1", 1, TimeUnit.HOURS);

// DECR — 原子扣减库存
redisUtil.decrement("seckill:stock:" + activityId);
```

### 12. Redis 过期策略？

**八股答案：**
- 定期删除：每隔 100ms 随机抽查设置了过期时间的 key，过期则删
- 惰性删除：访问 key 时检查，过期则删
- **内存淘汰策略**（如果没设过期时间或过期 key 没删完）：
  - `noeviction`：报错
  - `allkeys-lru`（推荐）：淘汰最近最少使用的
  - `volatile-lru`：从设置了过期时间的 key 中淘汰 LRU
  - `allkeys-random`、`volatile-random`、`volatile-ttl`

**项目里：**
```java
// Token 2h 过期
redisUtil.set("user_token:" + token, user, 2, TimeUnit.HOURS);

// 秒杀锁 1h 过期（防止锁未释放）
redisUtil.setIfAbsent("seckill:user:" + userId + ":" + activityId, "1", 1, TimeUnit.HOURS);
```

### 13. 缓存穿透、缓存击穿、缓存雪崩？

**八股答案：**
| 问题 | 现象 | 解决 |
|------|------|------|
| 穿透 | 查不存在的数据，每次穿过缓存到 DB | 布隆过滤器 / 缓存空值（短 TTL） |
| 击穿 | 热点 key 过期，高并发打到 DB | 互斥锁 / 逻辑过期不过期 |
| 雪崩 | 大量 key 同时过期 | 随机 TTL / 多级缓存 |

**项目里：**
```java
// 项目在 queryStoreBooks 中已移除 @Cacheable，每次都查 Redis/MySQL
// 但整体缺少缓存穿透防护
```
面试时可以主动说："我们项目没有做缓存空值处理，如果面试官你看我需要加我可以加上。"——体现你知道问题，也知道怎么解决。

### 14. Redis 分布式锁实现？

**八股答案：**
- SETNX + EXPIRE（不是原子操作 → 可能死锁）
- SET key value NX EX 30（原子操作 ✅）
- Redisson 看门狗（自动续期）
- RedLock（多节点，生产不常用）

**项目里：**
```java
// 秒杀一人一单锁 — 原子 SETNX
Boolean locked = redisUtil.setIfAbsent(
    "seckill:user:" + userId + ":" + activityId, 
    "1", 1, TimeUnit.HOURS
);
if (Boolean.FALSE.equals(locked)) {
    // 回滚库存 DECR
    throw new RuntimeException("每人限购一件");
}
```
**面试点**：这里锁粒度是 `userId + activityId`，粒度很细（只锁同一个用户的同一活动）。如果锁 `activityId`，高并发下所有用户会互相阻塞。

---

## 五、RabbitMQ

### 15. 为什么用消息队列？

**八股答案：**
- **异步处理**：不需要立即返回结果的操作（积分持久化）
- **削峰填谷**：秒杀高并发时平缓写入
- **解耦**：生产者不需要知道消费者的存在

**项目里：**
```java
// 秒杀成功后不立刻写库，发 MQ 异步处理
sendFlashSaleMqMessage(activity.getProductId(), userId, orderNo, seckillPrice);
// 消费者做：扣积分 + 创建单词本 + 复制单词 + 创建购买记录
```

### 16. 消息可靠性怎么保证？

**八股答案：**
| 阶段 | 问题 | 方案 |
|------|------|------|
| 生产 | 消息丢了 | 生产者确认 Confirm + Return |
| MQ | 交换机/队列挂了 | 持久化 Exchange/Queue/Message，镜像集群 |
| 消费 | 消费失败 | 手动 ACK + 重试 + 死信队列 |

**项目里：**
```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # 生产者确认
    publisher-returns: true             # 路由失败回调
    listener:
      simple:
        acknowledge-mode: manual        # 手动 ACK
```

```java
// 生产者确认回调
@PostConstruct
public void init() {
    rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
        if (!ack) log.error("MQ 发送失败: {}", cause);
    });
    rabbitTemplate.setReturnsCallback(returned -> {
        log.error("MQ 路由失败: {}", returned.getMessage());
    });
}
```

```java
// 消费者：手动 ACK + 重试 + 死信
@RabbitListener(queues = "seckill.queue")
public void consumeSeckill(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
    try {
        // 处理业务...
        channel.basicAck(tag, false);
    } catch (Exception e) {
        if (retryCount < 3) {
            channel.basicNack(tag, false, true);  // 重回队列
        } else {
            channel.basicNack(tag, false, false);  // 进入死信
        }
    }
}
```

### 17. 如何保证消息不重复消费（幂等性）？

**八股答案：**
- 天然幂等操作：`UPDATE stock = ? WHERE id = ?`（多次执行结果一样）
- 非幂等操作：用业务唯一键去重

**项目里：**
```sql
-- SeckillMessageLog 表
CREATE TABLE `seckill_message_log` (
  `order_no` varchar(64) NOT NULL COMMENT '订单号（唯一键）',
  `status` tinyint DEFAULT '0' COMMENT '0-已发送，1-已消费',
  `retry_count` int DEFAULT '0',
  PRIMARY KEY (`order_no`)
);

-- 消费前先检查
SeckillMessageLog log = seckillMessageLogMapper.selectById(orderNo);
if (log != null && log.getStatus() == 1) {
    // 已消费过，直接 ACK 跳过
    channel.basicAck(tag, false);
    return;
}
```

### 18. 死信队列的作用？

**八股答案：**
- 消息被拒绝且 `requeue=false`
- 消息 TTL 过期
- 队列达到最大长度
- 死信队列用于：延迟重试、异常监控、告警

**项目里：**
```java
@Bean
public Queue seckillDlq() {
    return QueueBuilder.durable("seckill.dlq").build();
}

// DlqConsumer 统一处理所有死信
@RabbitListener(queues = "#{T(java.util.Arrays).asList('seckill.dlq','points.dlq',...)}")
public void consumeDlq(Message message) {
    log.error("死信消息: {}", new String(message.getBody()));
    // 钉钉/邮件告警 → 人工处理
}
```

---

## 六、秒杀系统设计（项目最大亮点）

### 19. 秒杀系统的难点？你们怎么解决的？

这是面试中**最能展示深度**的问题，建议在简历上重点写。

| 难点 | 你的方案 |
|------|----------|
| **高并发读** | 秒杀页静态化 + CDN（你们没用），库存放 Redis |
| **高并发写** | Redis DECR 原子扣减，MySQL 不直接扛流量 |
| **超卖** | Redis DECR 保证不扣到负数 + MySQL 乐观锁兜底 |
| **一人一单** | SETNX 分布式锁（粒度：userId+activityId） |
| **重复消息** | SeckillMessageLog 幂等表 |
| **性能** | 同步只做 Redis 操作 + 创建订单，异步做积分/单词本 |

**完整流程**（建议背下来，面试流利说出）：
```
1. 前端请求 → 先检查 Redis 库存 > 0
2. Redis DECR 预扣库存（原子操作）
3. SETNX 检查是否已购买（每人限购 1 件）
4. ↓ 上面两步都通过 ↓
5. INSERT seckill_order（MySQL）
6. UPDATE seckill_activity SET stock = stock - 1 WHERE stock > 0（乐观锁兜底）
7. Redis 销售计数 +1
8. 发 MQ 异步：扣积分 + 创建单词书 + 复制单词 + 存购买记录
9. 返回秒杀成功
```

### 20. 如果有 100 万人同时秒杀 10 件商品，系统会怎样？

**思考路径：**
1. 前端/网关层就限流了（Nginx 限流、前端 Button 置灰）
2. Redis DECR 是原子操作，真正到 Redis 层的请求大部分会返回库存不足
3. MySQL 层只有不超过 10 个请求能通过乐观锁
4. **QPS 上限取决于 Redis 单机**：Redis 单机能扛 10w+ QPS，瓶颈不在中间件

**项目不足**：没有接口级限流（令牌桶/漏桶）。如果面试官问，说"可以加 Guava RateLimiter 或 Redis 令牌桶"。

### 21. Redis 扣库存和 MySQL 扣库存怎么保证一致性？

**八股答案：**
- 做不到强一致性（CAP），最终一致性
- 方案：Redis 预扣 → 创建订单 → MySQL 乐观锁扣真实库存
- 如果 Redis 扣了但 MySQL 扣失败：订单失败，Redis 库存用 SETNX 的 TTL 自动回滚 / 定时任务补偿

**项目里：**
```java
// 一旦 MySQL 乐观锁失败（库存被抢完），手动回滚 Redis
if (rows <= 0) {
    redisUtil.increment("seckill:stock:" + activityId, 1);  // 还回 Redis 库存
    throw new RuntimeException("库存不足");
}
```

---

## 七、MyBatis

### 22. MyBatis #{} 和 ${} 的区别？

**八股答案：**
- `#{}`：预编译，`?` 占位符，传参走参数绑定 → **防 SQL 注入**
- `${}`：直接拼接字符串 → **有 SQL 注入风险**

**项目里：**
```xml
<!-- 正确使用 #{} -->
<select id="selectById" resultType="User">
    SELECT * FROM user WHERE id = #{id}
</select>

<!-- 只有表名/排序字段等动态部分才用 ${}，且必须白名单校验 -->
<select id="selectAll" resultType="User">
    SELECT * FROM user ORDER BY ${orderColumn}
</select>
```

### 23. MyBatis 缓存机制？

**八股答案：**
- 一级缓存（SqlSession）：默认开启，同一个 SqlSession 内共享，增删改操作后清空
- 二级缓存（Mapper）：跨 SqlSession，需要配置 `<cache/>`，项目中很少用（分布式下有脏数据风险）

**项目里：**
没开二级缓存——面试可以说"我们项目分布式部署，二级缓存跨 JVM 不一致，所以用 Redis 代替"。

---

## 八、系统设计

### 24. 你们的项目架构优缺点？

**优点（面试说）：**
- 秒杀系统架构完整：Redis 预扣 + SETNX 一人一单 + 乐观锁 + MQ 异步
- RabbitMQ DLQ + 重试机制保障消息可靠性
- AI 能力与后端解耦（Python Agent 独立部署）
- Docker Compose 编排，开发部署一致

**改进（面试说，体现思考）：**
- 目前无单元测试，下一步计划用 JUnit 5 + Mockito 覆盖核心业务
- API Key 应通过环境变量注入而非硬编码在配置文件中
- 缺少接口级限流（可以用 Redis 令牌桶或 Guava RateLimiter）
- 缓存穿透防护（布隆过滤器）
- 统一全局异常处理器（消除 Controller try-catch）

### 25. 怎么保证数据一致性（Redis vs MySQL）？

**思路：** CAP 理论中选择 AP（可用性 + 分区容错），最终一致性
- Redis 做快速读写 → MQ 异步同步到 MySQL
- MySQL 乐观锁兜底（数据以 MySQL 为准）
- Redis 宕机时从 MySQL 加载数据

**项目里：**
```java
// 销售计数：Redis 为主，MySQL 兜底
String sales = redisUtil.get("store:sales:" + product.getId());
if (sales != null) {
    vo.setSalesCount(Integer.parseInt(sales));
} else {
    vo.setSalesCount(product.getSalesCount());  // 从 MySQL 读
}
```

---

## 九、面试应答技巧总结

| 面试官问 | 别只会说 | 结合项目说 |
|----------|----------|------------|
| "Redis 用过吗" | "存缓存" | "存 Token 2h 过期、秒杀库存 DECR、SETNX 分布式锁、销售计数 INCR" |
| "消息队列用过吗" | "发消息收消息" | "8 个队列 2 个交换机 DLQ，手动 ACK 重试 3 次进死信，SeckillMessageLog 幂等" |
| "秒杀怎么做的" | "用 Redis" | 把第 19 题的完整流程流利背出来 |
| "项目有什么难点" | "分页查询、登录" | "秒杀的并发控制和一致性、MQ 的可靠性、AI Agent 集成" |
| "还有什么要问的" | "没什么了" | "我们项目目前没有单元测试，你们团队怎么保证代码质量的？" |
