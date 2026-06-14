package org.example.mq.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息生产者
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;

    /**
     * 发送用户注册消息
     * 
     * @param userId 用户ID
     * @param username 用户名
     */
    public void sendUserRegisterMessage(Long userId, String username) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("username", username);
        message.put("timestamp", System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_USER_REGISTER,
            message
        );
        
        log.info("📤 [生产者] 发送用户注册消息 | 用户ID: {} | 用户名: {}", userId, username);
    }
    
    /**
     * 发送用户登录消息（用于异步缓存用户信息）
     * 
     * @param userId 用户ID
     */
    public void sendUserLoginMessage(Long userId,String token) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("timestamp", System.currentTimeMillis());
        message.put("token", token);
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_USER_LOGIN,
            message
        );
        
        log.info("📤 [生产者] 发送用户登录消息 | 用户ID: {}", userId);
    }
    
    /**
     * 发送积分奖励消息
     * 
     * @param userId 用户ID
     * @param points 积分数量
     * @param reason 奖励原因
     */
    public void sendPointsRewardMessage(Long userId, Integer points, String reason) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("points", points);
        message.put("reason", reason);
        message.put("timestamp", System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_POINTS_REWARD,
            message
        );
        
        log.info("📤 [生产者] 发送积分奖励消息 | 用户ID: {} | 积分: {} | 原因: {}", userId, points, reason);
    }
    
    /**
     * 发送通知消息
     * 
     * @param type 通知类型（email/sms/push）
     * @param content 通知内容
     * @param userId 用户ID
     */
    public void sendNotificationMessage(String type, String content, Long userId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("content", content);
        message.put("userId", userId);
        message.put("timestamp", System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_TOPIC,
            "notification." + type,
            message
        );
        
        log.info("📤 [生产者] 发送通知消息 | 类型: {} | 用户ID: {}", type, userId);
    }
    
    /**
     * 发送购买消息（用于异步处理单词复制等非关键路径操作）
     * 
     * @param message 购买消息内容
     */
    public void sendPurchaseMessage(Map<String, Object> message) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_PURCHASE,
            message
        );
        
        log.info("📤 [生产者] 发送购买消息 | 用户ID: {} | 商品ID: {} | 用户书ID: {}", 
                message.get("userId"), message.get("productId"), message.get("userBookId"));
    }

    public void CreateUserAccount(Long id) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_USER_ACCOUNT,
            id
        );
    }
    
    /**
     * 发送秒杀消息（用于异步处理秒杀订单）
     */
    public void sendSeckillMessage(Map<String, Object> message) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_DIRECT,
            RabbitMQConfig.ROUTING_KEY_SECKILL,
            message
        );
        
        log.info("📤 [生产者] 发送秒杀消息 | 用户ID: {} | 活动ID: {} | 订单号: {}", 
                message.get("userId"), message.get("activityId"), message.get("orderNo"));
    }
    public void SendPointDeductMessage(long userId,Integer points,String orderNumber){
        Map<String,Object>message = new HashMap<>();
        message.put("userId", userId);
        message.put("points", points);
        message.put("orderNo", orderNumber);
        message.put("timestamp", System.currentTimeMillis());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_DIRECT,
                rabbitMQConfig.ROUTING_KEY_POINTS_DEDUCT,
                message
        );
        log.info("📤 [生产者] 发送积分落库消息 | 用户ID: {} | 积分: {} | 订单号: {}", userId, points, orderNumber);
    }
}
