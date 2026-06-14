package org.example.mq.consumer;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 死信队列消费者
 * 统一处理所有业务队列重试失败后转入死信队列的消息
 */
@Slf4j
@Component
public class DlqConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DLQ_ALL)
    public void consumeDeadLetter(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String originalQueue = "unknown";
            Object xDeath = message.getMessageProperties().getHeader("x-death");
            if (xDeath instanceof java.util.List && !((java.util.List<?>) xDeath).isEmpty()) {
                Object firstDeath = ((java.util.List<?>) xDeath).get(0);
                if (firstDeath instanceof java.util.Map) {
                    Object queue = ((java.util.Map<?, ?>) firstDeath).get("queue");
                    if (queue != null) {
                        originalQueue = queue.toString();
                    }
                }
            }

            log.error("💀 [DLQ] 收到死信消息 | 原队列: {} | 路由键: {} | 投递标签: {}",
                    originalQueue,
                    message.getMessageProperties().getReceivedRoutingKey(),
                    deliveryTag);

            log.warn("💀 [DLQ] 消息内容: {}", new String(message.getBody()));

            // TODO: 可扩展 - 将死信记录到数据库或发送告警通知

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("❌ [DLQ] 处理死信消息失败", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
