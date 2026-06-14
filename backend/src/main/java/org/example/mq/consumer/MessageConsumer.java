package org.example.mq.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.annotation.RabbitMqMessage;
import org.example.annotation.RabbitTime;
import org.example.config.RabbitMQConfig;
import org.example.context.UserContextHolder;
import org.example.entity.PublicBookWord;
import org.example.entity.PublicVocabularyBook;
import org.example.entity.User;
import org.example.entity.UserPointsAccount;
import org.example.entity.UserVocabularyBook;
import org.example.mapper.PublicBookWordMapper;
import org.example.mapper.PublicVocabularyBookMapper;
import org.example.service.UserBookWordService;
import org.example.service.UserPointsAccountService;
import org.example.service.UserService;
import org.example.service.UserVocabularyBookService;
import org.example.mapper.SeckillActivityMapper;
import org.example.mapper.SeckillMessageLogMapper;
import org.example.mapper.SeckillOrderMapper;
import org.example.entity.SeckillActivity;
import org.example.entity.SeckillMessageLog;
import org.example.entity.StoreProduct;
import org.example.entity.StorePurchaseRecord;
import org.example.mapper.StoreProductMapper;
import org.example.mapper.StorePurchaseRecordMapper;
import org.example.mapper.UserVocabularyBookMapper;
import org.example.constant.RedisKeys;
import org.example.utils.RedisUtil;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.example.utils.UserEnum.USER_TOKEN;

/**
 * 消息消费者
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MessageConsumer {

    private final Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();

    private final RedisUtil redisUtil;
    private final UserService userService;
    private final UserVocabularyBookService userVocabularyBookService;
    private final UserPointsAccountService userPointsAccountService;
    private final UserBookWordService userBookWordService;
    private final PublicBookWordMapper publicBookWordMapper;
    private final PublicVocabularyBookMapper publicVocabularyBookMapper;
    private final SeckillMessageLogMapper seckillMessageLogMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final StoreProductMapper storeProductMapper;
    private final StorePurchaseRecordMapper storePurchaseRecordMapper;
    private final UserVocabularyBookMapper userVocabularyBookMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    /**
     * 消费用户注册消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_REGISTER)
    public void consumeUserRegister(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            // 解析消息
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);
            Long userId = ((Number) body.get("userId")).longValue();
            String username = (String) body.get("username");
            
            log.info("📥 [消费者] 处理用户注册消息 | 用户ID: {} | 用户名: {}", userId, username);
            
            // TODO: 在这里处理业务逻辑
            // 例如：发送欢迎邮件、初始化用户数据等
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.debug("✅ [消费者] 消息已确认 | 用户ID: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ [消费者] 处理用户注册消息失败", e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * 消费用户登录消息（异步缓存用户信息到Redis）
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_LOGIN)
    public void consumeUserLogin(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);
            
            Long userId = ((Number) body.get("userId")).longValue();
            String token = (String) body.get("token");
            log.info("📥 [消费者] 开始缓存用户信息 | 用户ID: {}", userId);

            User user = userService.getById(userId);
            List<UserVocabularyBook> books = userVocabularyBookService.listByUserId(userId);
            UserPointsAccount points = userPointsAccountService.getAccountByUserId(userId);

            redisUtil.set(RedisKeys.userBooks(userId), books, 2, TimeUnit.HOURS);
            if (points != null) {
                redisUtil.set(RedisKeys.userPoints(userId), points.getBalance(), 2, TimeUnit.HOURS);
            }
            String redisKey = USER_TOKEN.getValue() + ":" + token;

            redisUtil.set(redisKey, user, 2, TimeUnit.HOURS);
            
            log.info("✅ [消费者] 用户信息缓存完成 | 用户ID: {}", userId);
            log.debug("💾 [缓存用户信息] 缓存完成 | 用户Token: {}", token);
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("❌ [消费者] 缓存用户信息失败", e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * 消费积分奖励消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_POINTS_REWARD)
    public void consumePointsReward(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);
            
            Long userId = ((Number) body.get("userId")).longValue();
            Integer points = ((Number) body.get("points")).intValue();
            String reason = (String) body.get("reason");
            
            log.info("📥 [消费者] 处理积分奖励消息 | 用户ID: {} | 积分: {} | 原因: {}", userId, points, reason);
            
            // TODO: 在这里处理积分增加逻辑
            // 例如：调用 PointsAccountService.addPoints()
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.debug("✅ [消费者] 消息已确认 | 用户ID: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ [消费者] 处理积分奖励消息失败", e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * 消费通知消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION)
    public void consumeNotification(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);
            
            String type = (String) body.get("type");
            String content = (String) body.get("content");
            Long userId = ((Number) body.get("userId")).longValue();
            
            log.info("📥 [消费者] 处理通知消息 | 类型: {} | 用户ID: {} | 内容: {}", type, userId, content);
            
            // TODO: 在这里处理通知逻辑
            // 例如：发送邮件、短信、推送等
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.debug("✅ [消费者] 消息已确认 | 用户ID: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ [消费者] 处理通知消息失败", e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * 消费购买消息（异步处理单词复制等非关键路径操作）
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_PURCHASE)
    public void consumePurchase(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        long startTime = System.currentTimeMillis();
        long stepStart;
        
        try {
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);
            
            Long userId = ((Number) body.get("userId")).longValue();
            Long productId = ((Number) body.get("productId")).longValue();
            Long userBookId = ((Number) body.get("userBookId")).longValue();
            Long publicBookId = body.get("publicBookId") != null ? ((Number) body.get("publicBookId")).longValue() : null;
            Integer pricePaid = ((Number) body.get("pricePaid")).intValue();
            
            log.info("⏱️ [异步消费者-总] 开始处理购买消息 | 用户ID: {} | 商品ID: {} | 用户书ID: {}", 
                    userId, productId, userBookId);
            
            // 步骤1：复制单词关联（最耗时的操作）
            stepStart = System.currentTimeMillis();
            if (publicBookId != null) {
                // 查询公共单词书中的所有单词
                List<PublicBookWord> publicBookWords = publicBookWordMapper.selectByBookId(publicBookId);
                
                if (publicBookWords != null && !publicBookWords.isEmpty()) {
                    // 批量插入用户单词关联
                    int count = userBookWordService.batchAddWordsToBook(userId, userBookId, publicBookWords);
                    log.info("⏱️ [异步消费者-步骤1] 复制单词完成 | 数量: {} | 耗时: {}ms", count, System.currentTimeMillis() - stepStart);
                } else {
                    log.warn("⚠️ [异步消费者] 公共单词书没有单词 | 公共书ID: {}", publicBookId);
                }
            } else {
                log.warn("⚠️ [异步消费者] 未提供公共单词书ID");
            }
            
            // 步骤2：更新单词书的单词数量
            stepStart = System.currentTimeMillis();
            if (publicBookId != null) {
                PublicVocabularyBook publicBook = publicVocabularyBookMapper.selectById(publicBookId);
                if (publicBook != null) {
                    userVocabularyBookService.updateVocabularyBook(
                        userBookId, 
                        null, 
                        null, 
                        null, 
                        null
                    );
                    // TODO: 需要添加 updateWordCount 方法
                    log.info("⏱️ [异步消费者-步骤2] 更新单词书统计 | 耗时: {}ms", System.currentTimeMillis() - stepStart);
                }
            }
            
            // 步骤3：更新商品销售数量
            stepStart = System.currentTimeMillis();
            // TODO: 调用 StoreProductMapper.updateSalesCount
            log.info("⏱️ [异步消费者-步骤3] 更新销售统计 | 耗时: {}ms", System.currentTimeMillis() - stepStart);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.info("✅ [异步消费者-总计] 购买消息处理完成 | 用户ID: {} | 总耗时: {}ms", userId, duration);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("❌ [异步消费者] 处理购买消息失败 | 错误: {}", e.getMessage(), e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [异步消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [异步消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
    /**
     * 消费用户账户创建消息（异步初始化用户账户数据）
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_ACCOUNT)
    @RabbitMqMessage(message = "来源:/api/user/register")
    @RabbitTime(message = "用户账户创建消息处理耗时")
    public void consumeUserAccount(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
            
        try {
            // 直接获取用户ID（生产者发送的是Long类型）
            Long userId = (Long) messageConverter.fromMessage(message);
                
            log.info("📥 [消费者] 开始处理用户账户初始化 | 用户ID: {}", userId);
            userPointsAccountService.createAccount(userId);
            log.info("📤 [消费者] 用户账户初始化完成 | 用户ID: {}", userId);
            
            // ✅ 成功时确认消息
            channel.basicAck(deliveryTag, false);
                
        } catch (Exception e) {
            log.error("❌ [消费者] 处理用户账户初始化失败 | 错误: {}", e.getMessage(), e);
            
            // 获取重试次数
            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) {
                retryCount = 0;
            }
            
            // 最大重试次数
            final int MAX_RETRY = 3;
            
            if (retryCount < MAX_RETRY) {
                // 增加重试次数
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                
                log.warn("⚠️ [消费者] 第 {} 次重试 | 用户ID: {}", retryCount, UserContextHolder.getUserId());
                
                // 重新入队
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数({})，丢弃消息 | 用户ID: {}", MAX_RETRY,  UserContextHolder.getUserId());
                
                // ❌ 拒绝消息，不再重新入队（进入死信队列或丢弃）
                channel.basicNack(deliveryTag, false, false);
                
                // TODO: 发送告警通知人工处理
            }
        }
    }
    
    /**
     * 消费秒杀消息（异步处理秒杀订单的后续操作）
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_SECKILL)
    public void consumeSeckill(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Map<String, Object> body = null;

        try {
            body = (Map<String, Object>) messageConverter.fromMessage(message);

            Long userId = ((Number) body.get("userId")).longValue();
            Long activityId = ((Number) body.get("activityId")).longValue();
            String orderNo = (String) body.get("orderNo");

            // 从数据库查询秒杀价格，不依赖客户端传值
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                throw new RuntimeException("秒杀活动不存在 | 活动ID: " + activityId);
            }
            Integer price = activity.getSeckillPrice();
            
            // 幂等性检查：通过订单号（messageId）查询是否已处理
            SeckillMessageLog messageLog = seckillMessageLogMapper.selectByMessageId(orderNo);
            if (messageLog != null && messageLog.getStatus() == 1) {
                log.info("✅ [异步消费者-秒杀] 消息已处理，跳过 | 订单号: {}", orderNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 如果日志不存在（兜底），创建一个
            if (messageLog == null) {
                SeckillMessageLog newLog = new SeckillMessageLog();
                newLog.setMessageId(orderNo);
                newLog.setMessageContent(String.format("userId=%d,activityId=%d", userId, activityId));
                newLog.setStatus(0);
                seckillMessageLogMapper.insert(newLog);
            }
            
            log.info("⏱️ [异步消费者-秒杀] 开始处理 | 用户ID: {} | 活动ID: {} | 订单号: {}", 
                    userId, activityId, orderNo);
            
            // 步骤1：扣除积分（传入订单号作为幂等性标识）
            userPointsAccountService.deductPoints(
                userId,
                price,
                6,
                "秒杀商品",
                activityId,
                orderNo
            );

            // 步骤2：创建用户单词书并复制单词
            Long productId = body.get("productId") != null ? ((Number) body.get("productId")).longValue() : activity.getProductId();
            if (productId != null) {
                StoreProduct product = storeProductMapper.selectById(productId);
                if (product != null) {
                    // 创建用户单词书
                    UserVocabularyBook userBook = new UserVocabularyBook();
                    userBook.setUserId(userId);
                    userBook.setBookName(product.getProductName());
                    userBook.setDescription(product.getDescription());
                    userBook.setCoverImage(product.getCoverImage());
                    userBook.setWordCount(0);
                    userBook.setIsPublic(0);
                    userBook.setSourceType(2); // 2-从商店购买
                    userBook.setSourceStoreBookId(productId);
                    userBook.setCreatedAt(LocalDateTime.now());
                    userBook.setUpdatedAt(LocalDateTime.now());
                    userVocabularyBookMapper.insert(userBook);
                    Long userBookId = userBook.getId();

                    // 复制单词关联
                    if (product.getReferenceId() != null) {
                        List<PublicBookWord> publicBookWords = publicBookWordMapper.selectByBookId(product.getReferenceId());
                        if (publicBookWords != null && !publicBookWords.isEmpty()) {
                            int wordCount = userBookWordService.batchAddWordsToBook(userId, userBookId, publicBookWords);
                            userVocabularyBookMapper.updateWordCount(userBookId, wordCount);
                            log.info("📋 [异步消费者-秒杀] 复制单词完成 | 用户书ID: {} | 单词数: {}", userBookId, wordCount);
                        }
                    }

                    // 记录购买记录
                    StorePurchaseRecord purchaseRecord = new StorePurchaseRecord();
                    purchaseRecord.setUserId(userId);
                    purchaseRecord.setProductId(productId);
                    purchaseRecord.setPricePaid(price);
                    purchaseRecord.setPurchaseType(3); // 3-秒杀购买
                    purchaseRecord.setUserBookId(userBookId);
                    purchaseRecord.setCreatedAt(LocalDateTime.now());
                    storePurchaseRecordMapper.insert(purchaseRecord);

                    log.info("📚 [异步消费者-秒杀] 单词书已入库 | 用户书ID: {}", userBookId);
                }
            }
            
            // 更新消息状态为成功（幂等性标记）
            seckillMessageLogMapper.updateStatus(orderNo, 1);

            // 更新订单状态为已完成
            seckillOrderMapper.updateStatusByOrderNo(orderNo, 1);

            log.info("✅ [异步消费者-秒杀] 处理完成 | 用户ID: {} | 订单号: {}", userId, orderNo);
            
            // ✅ 手动确认消息
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("❌ [异步消费者-秒杀] 处理失败 | 错误: {}", e.getMessage(), e);
            
            // 获取重试次数
            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) {
                retryCount = 0;
            }
            
            final int MAX_RETRY = 3;
            
            if (retryCount < MAX_RETRY) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);

                if (body != null && body.get("orderNo") != null) {
                    seckillMessageLogMapper.incrementRetryCount((String) body.get("orderNo"));
                }

                log.warn("⚠️ [异步消费者-秒杀] 第 {} 次重试 | 订单号: {}", retryCount,
                        message.getMessageProperties().getHeaders().get("orderNo"));

                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [异步消费者-秒杀] 达到最大重试次数({})，丢弃消息 | 订单号: {}",
                        MAX_RETRY, message.getMessageProperties().getHeaders().get("orderNo"));

                // 标记订单为异常状态
                if (body != null && body.get("orderNo") != null) {
                    seckillOrderMapper.updateStatusByOrderNo((String) body.get("orderNo"), 2);
                }

                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
    @RabbitListener(queues = RabbitMQConfig.QUEUE_POINTS_DEDUCT)
    @RabbitMqMessage(message = "积分扣除")
    public void consumePointsDeduct(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<String, Object> body = (Map<String, Object>) messageConverter.fromMessage(message);

            Long userId = ((Number) body.get("userId")).longValue();
            Integer points = ((Number) body.get("points")).intValue();
            String orderNo = (String) body.get("orderNo");

            log.info("📥 [消费者] 处理积分落库 | 用户ID: {} | 积分: {} | 订单号: {}", userId, points, orderNo);

            userPointsAccountService.deductPoints(
                    userId, points,
                    1,              // type: 1=购买扣减（参考其他调用的传参）
                    "购买商品扣积分",
                    null,           // referenceId 传 null
                    orderNo         // idempotencyKey 用订单号防重复
            );

            channel.basicAck(deliveryTag, false);
            log.info("✅ [消费者] 积分落库完成 | 用户ID: {}", userId);

        } catch (Exception e) {
            log.error("❌ [消费者] 积分落库失败", e);

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [消费者] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [消费者] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
