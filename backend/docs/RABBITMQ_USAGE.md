# 🐰 RabbitMQ 配置与使用指南

## 📋 目录
- [1. 环境准备](#1-环境准备)
- [2. 配置说明](#2-配置说明)
- [3. 项目结构](#3-项目结构)
- [4. 使用方法](#4-使用方法)
- [5. 测试示例](#5-测试示例)

---

## 1. 环境准备

### 安装 RabbitMQ（使用 Docker）

```bash
# 启动 RabbitMQ（包含管理界面）
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management

# 查看运行状态
docker ps | grep rabbitmq

# 查看日志
docker logs rabbitmq
```

### 访问管理界面

- **地址**: http://localhost:15672
- **用户名**: guest
- **密码**: guest

在管理界面中，你可以：
- 查看队列、交换机、绑定关系
- 监控消息流量
- 手动发送/接收消息进行测试

---

## 2. 配置说明

### application.yml 配置

```yaml
spring:
  rabbitmq:
    host: localhost          # RabbitMQ 服务器地址
    port: 5672              # AMQP 端口
    username: guest         # 用户名
    password: guest         # 密码
    virtual-host: /         # 虚拟主机
    listener:
      simple:
        acknowledge-mode: manual  # 手动确认模式
        concurrency: 5            # 最小消费者数量
        max-concurrency: 10       # 最大消费者数量
        prefetch: 1               # 每次只处理一条消息
    template:
      mandatory: true             # 强制路由（确保消息被路由）
    publisher-confirm-type: correlated  # 发布确认机制
    publisher-returns: true       # 发布返回机制
```

### 配置项说明

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| acknowledge-mode | 消息确认模式 | manual（手动确认） |
| concurrency | 初始消费者数量 | 5 |
| max-concurrency | 最大消费者数量 | 10 |
| prefetch | 每个消费者预取消息数 | 1 |
| mandatory | 强制路由 | true |
| publisher-confirm-type | 发布确认类型 | correlated |

---

## 3. 项目结构

```
src/main/java/org/example/
├── config/
│   └── RabbitMQConfig.java          # RabbitMQ 配置类
├── mq/
│   ├── producer/
│   │   └── MessageProducer.java     # 消息生产者
│   └── consumer/
│       └── MessageConsumer.java     # 消息消费者
└── controller/
    └── UserController.java          # 使用示例
```

### 核心组件

#### 1️⃣ RabbitMQConfig.java

定义了：
- **3个队列**：用户注册、积分奖励、通知
- **2个交换机**：直连交换机、主题交换机
- **3个绑定关系**：队列与交换机的绑定
- **消息转换器**：Jackson2JsonMessageConverter
- **RabbitTemplate**：配置发布确认和返回回调

#### 2️⃣ MessageProducer.java

提供3个发送消息的方法：
- `sendUserRegisterMessage()` - 发送用户注册消息
- `sendPointsRewardMessage()` - 发送积分奖励消息
- `sendNotificationMessage()` - 发送通知消息

#### 3️⃣ MessageConsumer.java

提供3个消费消息的方法：
- `consumeUserRegister()` - 消费用户注册消息
- `consumePointsReward()` - 消费积分奖励消息
- `consumeNotification()` - 消费通知消息

---

## 4. 使用方法

### 基本使用流程

```java
@Autowired
private MessageProducer messageProducer;

// 1. 发送用户注册消息
messageProducer.sendUserRegisterMessage(userId, username);

// 2. 发送积分奖励消息
messageProducer.sendPointsRewardMessage(userId, 100, "签到奖励");

// 3. 发送通知消息
messageProducer.sendNotificationMessage("email", "欢迎注册！", userId);
```

### 在业务代码中集成

#### 示例1：用户注册时发送消息

```java
@PostMapping("/register")
public Result<User> register(@RequestParam String username, 
                              @RequestParam String password) {
    User user = userService.register(username, password);
    
    // 发送注册消息到RabbitMQ
    messageProducer.sendUserRegisterMessage(user.getId(), username);
    
    return Result.success("注册成功", user);
}
```

#### 示例2：签到成功后发送积分奖励消息

```java
@PostMapping("/checkin")
public Result checkin() {
    Long userId = UserContextHolder.getUserId();
    CheckinVO result = checkinService.checkin(userId);
    
    // 发送积分奖励消息
    if (result.getPointsEarned() > 0) {
        messageProducer.sendPointsRewardMessage(
            userId, 
            result.getPointsEarned(), 
            "每日签到"
        );
    }
    
    return Result.success("签到成功", result);
}
```

#### 示例3：购买成功后发送通知消息

```java
@PostMapping("/books/{id}/purchase")
public Result purchaseBook(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    
    // 执行购买逻辑...
    
    // 发送通知消息
    messageProducer.sendNotificationMessage(
        "push", 
        "购买成功！单词书已添加到您的单词本", 
        userId
    );
    
    return Result.success("购买成功");
}
```

---

## 5. 测试示例

### 方式1：通过接口测试

1. 启动应用
2. 调用注册接口：

```bash
curl -X POST http://localhost:8080/api/user/register \
  -d "username=testuser&password=123456&nickname=Test"
```

3. 查看控制台日志，应该看到：

```
📤 [生产者] 发送用户注册消息 | 用户ID: 1 | 用户名: testuser
📥 [消费者] 处理用户注册消息 | 用户ID: 1 | 用户名: testuser
✅ [消费者] 消息已确认 | 用户ID: 1
```

### 方式2：通过管理界面测试

1. 访问 http://localhost:15672
2. 登录（guest/guest）
3. 点击 "Queues" 标签
4. 选择一个队列（如 `queue.user.register`）
5. 展开 "Publish message" 区域
6. 输入消息内容（JSON格式）：

```json
{
  "userId": 999,
  "username": "test_from_ui",
  "timestamp": 1234567890
}
```

7. 点击 "Publish message"
8. 查看应用日志，确认消息被消费

### 方式3：编写单元测试

```java
@SpringBootTest
class MessageProducerTest {
    
    @Autowired
    private MessageProducer messageProducer;
    
    @Test
    void testSendUserRegisterMessage() {
        messageProducer.sendUserRegisterMessage(1L, "testuser");
        
        // 等待一段时间让消费者处理
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    void testSendPointsRewardMessage() {
        messageProducer.sendPointsRewardMessage(1L, 100, "测试奖励");
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

---

## 🔧 常见问题

### Q1: 消息没有被消费？

**检查清单**：
- ✅ RabbitMQ 服务是否正常运行
- ✅ 队列是否正确创建并绑定
- ✅ 消费者是否正确配置 `@RabbitListener`
- ✅ 网络连接是否正常

### Q2: 消息丢失怎么办？

**解决方案**：
- 使用持久化队列（已配置）
- 开启手动确认模式（已配置）
- 开启发布确认机制（已配置）
- 处理异常时重新入队（已实现）

### Q3: 如何查看消息队列状态？

**方法**：
1. 访问 RabbitMQ 管理界面：http://localhost:15672
2. 查看队列中的消息数量
3. 查看消费者数量
4. 查看消息流量图表

### Q4: 如何处理大量消息？

**优化建议**：
- 增加消费者并发数（调整 `concurrency` 和 `max-concurrency`）
- 使用批量处理（在消费者中累积多条消息后批量处理）
- 优化业务逻辑，减少单次处理时间
- 监控队列长度，设置告警

---

## 📊 架构流程图

```
用户请求 → Controller → MessageProducer → Exchange → Queue → MessageConsumer → 业务处理
                                    ↓
                              RabbitMQ Broker
                                    ↓
                            (持久化、路由、负载均衡)
```

### 消息流转过程：

1. **Controller** 接收用户请求
2. **MessageProducer** 发送消息到 Exchange
3. **Exchange** 根据 Routing Key 路由到对应的 Queue
4. **Queue** 存储消息（持久化）
5. **MessageConsumer** 从 Queue 消费消息
6. **业务处理** 完成后手动确认消息
7. **Queue** 删除已确认的消息

---

## 🎯 最佳实践

### 1. 消息幂等性

确保消息可以被重复消费而不产生副作用：

```java
// 使用唯一标识防止重复处理
String messageId = (String) body.get("messageId");
if (alreadyProcessed(messageId)) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

### 2. 错误处理

```java
try {
    // 处理消息
} catch (BusinessException e) {
    // 业务异常，拒绝消息但不重新入队
    channel.basicNack(deliveryTag, false, false);
} catch (Exception e) {
    // 系统异常，拒绝消息并重新入队
    channel.basicNack(deliveryTag, false, true);
}
```

### 3. 日志记录

```java
log.info("📥 [消费者] 开始处理消息 | 消息ID: {} | 内容: {}", messageId, content);
// 处理逻辑
log.info("✅ [消费者] 消息处理成功 | 消息ID: {}", messageId);
```

### 4. 性能监控

- 监控队列长度
- 监控消息处理时间
- 监控消费者健康状态
- 设置告警阈值

---

## 🚀 下一步

现在你已经完成了 RabbitMQ 的基本配置，可以：

1. ✅ 启动 RabbitMQ 服务
2. ✅ 启动 Spring Boot 应用
3. ✅ 调用接口测试消息发送和消费
4. ✅ 在管理界面查看消息流转
5. ✅ 根据业务需求扩展更多队列和消费者

祝使用愉快！🎉
