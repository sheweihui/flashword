package org.example.mq.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
@Slf4j
public class UserMessageProducer {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;

    /**
     * 发送用户登录消息（用于异步缓存用户相关数据）
     */
    public void sendUserLoginMessage(Long userId, String token) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("token", token);
        message.put("timestamp", System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.USER_MESSAGE_EXCHANGE, 
            RabbitMQConfig.USER_MESSAGE_ROUTING_KEY, 
            message
        );
        
        log.info("📤 [UserMessageProducer] 发送用户登录消息 | 用户ID: {} | Token: {}", userId, token);
    }
}
