package org.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                        RabbitMQ 消息路由对照表                               ║
 * ╠══════════════════════╦═════════════════════╦════════════════════╦═══════════╣
 * ║   生产者方法          ║   路由键             ║   队列              ║ 消费者   ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendUserRegister     ║ user.register       ║ queue.user.        ║ consume   ║
 * ║ Message()            ║                     ║ register           ║ UserReg.. ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendUserLogin        ║ user.login          ║ queue.user.login   ║ consume   ║
 * ║ (MessageProducer)    ║                     ║                    ║ UserLogin ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendUserLogin        ║ user.message        ║ USER_MESSAGE_QUEUE ║ consume   ║
 * ║ (UserMessageProducer)║                     ║                    ║ (UserMsg) ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendPointsReward     ║ points.reward       ║ queue.points.      ║ consume   ║
 * ║ Message()            ║                     ║ reward             ║ PointsRwd ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendNotification     ║ notification.{type} ║ queue.notification ║ consume   ║
 * ║ Message()            ║                     ║                    ║ Notificat ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendPurchase         ║ purchase.async      ║ queue.purchase     ║ consume   ║
 * ║ Message()            ║                     ║                    ║ Purchase  ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ CreateUserAccount()  ║ user.account.create ║ queue.user.account ║ consume   ║
 * ║                      ║                     ║                    ║ UserAccnt ║
 * ╠══════════════════════╬═════════════════════╬════════════════════╬═══════════╣
 * ║ sendSeckill          ║ seckill.order       ║ queue.seckill      ║ consume   ║
 * ║ Message()            ║                     ║                    ║ Seckill   ║
 * ╚══════════════════════╩═════════════════════╩════════════════════╩═══════════╝
 */
@Slf4j
@Configuration
public class RabbitMQConfig {
    
    // ==================== 交换机名称常量 ====================
    /**
     * 直连交换机 - 用于精确匹配路由键的消息
     */
    public static final String EXCHANGE_DIRECT = "exchange.direct";
    
    /**
     * 主题交换机 - 支持通配符匹配的消息
     */
    public static final String EXCHANGE_TOPIC = "exchange.topic";
    
    /**
     * 用户消息直连交换机
     */
    public static final String USER_MESSAGE_EXCHANGE = "USER_MESSAGE_EXCHANGE";

    /** 死信交换机 */
    public static final String EXCHANGE_DLX = "exchange.dlx";

    // ==================== 队列名称常量 ====================
    /**
     * 用户注册队列
     */
    public static final String QUEUE_USER_REGISTER = "queue.user.register";
    
    /**
     * 用户登录队列（用于异步缓存用户信息）
     */
    public static final String QUEUE_USER_LOGIN = "queue.user.login";
    
    /**
     * 积分奖励队列
     */
    public static final String QUEUE_POINTS_REWARD = "queue.points.reward";
    
    /**
     * 通知队列
     */
    public static final String QUEUE_NOTIFICATION = "queue.notification";
    
    /**
     * 购买队列（用于异步处理单词复制等操作）
     */
    public static final String QUEUE_PURCHASE = "queue.purchase";
    
    /**
     * 用户账户初始化队列
     */
    public static final String QUEUE_USER_ACCOUNT = "queue.user.account";
    
    /**
     * 秒杀队列
     */
    public static final String QUEUE_SECKILL = "queue.seckill";
    
    /**
     * 用户消息队列（用于异步缓存用户相关数据）
     */
    public static final String USER_MESSAGE_QUEUE = "USER_MESSAGE_QUEUE";
    /**
     * 积分落库队列
     */
    public static final String QUEUE_POINTS_DEDUCT = "queue.points.deduct";
    /**
     * 积分落库路由键
     */
    public static final String ROUTING_KEY_POINTS_DEDUCT = "points.deduct";


    /** 统一死信队列 */
    public static final String QUEUE_DLQ_ALL = "queue.dlq.all";

    // ==================== 路由键常量 ====================
    /**
     * 用户注册路由键
     */
    public static final String ROUTING_KEY_USER_REGISTER = "user.register";
    
    /**
     * 用户登录路由键
     */
    public static final String ROUTING_KEY_USER_LOGIN = "user.login";
    
    /**
     * 积分奖励路由键
     */
    public static final String ROUTING_KEY_POINTS_REWARD = "points.reward";
    
    /**
     * 通知路由键（支持通配符）
     */
    public static final String ROUTING_KEY_NOTIFICATION = "notification.#";
    
    /**
     * 购买路由键
     */
    public static final String ROUTING_KEY_PURCHASE = "purchase.async";
    
    /**
     * 用户账户初始化路由键
     */
    public static final String ROUTING_KEY_USER_ACCOUNT = "user.account.create";
    
    /**
     * 秒杀路由键
     */
    public static final String ROUTING_KEY_SECKILL = "seckill.order";
    
    /**
     * 用户消息路由键
     */
    public static final String USER_MESSAGE_ROUTING_KEY = "user.message";

    // ==================== 消息转换器配置 ====================
    /**
     * JSON消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        
        // 开启mandatory模式（确保消息路由失败时返回）
        rabbitTemplate.setMandatory(true);
        
        // 设置发布确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("✅ [RabbitMQ] 消息发送成功");
            } else {
                log.error("❌ [RabbitMQ] 消息发送失败: {}", cause);
            }
        });
        
        // 设置返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("❌ [RabbitMQ] 消息路由失败: {} | 路由键: {} | 交换机: {}", 
                    returned.getMessage(), 
                    returned.getRoutingKey(), 
                    returned.getExchange());
        });
        
        return rabbitTemplate;
    }

    // ==================== RabbitAdmin（声明所有 Queue/Exchange/Binding） ====================
    /**
     * RabbitAdmin 负责将所有 Queue、Exchange、Binding Bean 声明到 RabbitMQ 服务端。
     * <p>
     * 所有业务队列在此处定义为 Bean（含 x-dead-letter-exchange 参数），
     * RabbitMQConfig 是 @Configuration 类，Bean 优先于 @Component 消费者初始化，
     * 因此 RabbitAdmin 在监听器容器启动前完成队列声明，避免参数冲突。
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // ==================== 交换机定义 ====================
    /**
     * 创建直连交换机
     */
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(EXCHANGE_DIRECT, true, false);
    }
    
    /**
     * 创建主题交换机
     */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(EXCHANGE_TOPIC, true, false);
    }
    
    /**
     * 创建用户消息直连交换机
     */
    @Bean
    public DirectExchange userMessageExchange() {
        return new DirectExchange(USER_MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }
    @Bean
    public Queue dlqAll() {
        return QueueBuilder.durable(QUEUE_DLQ_ALL).build();
    }
    @Bean
    public Binding dlqAllBinding(Queue dlqAll, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqAll)
                .to(dlxExchange)
                .with("#");
    }

}
