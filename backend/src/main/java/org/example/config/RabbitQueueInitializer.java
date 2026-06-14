package org.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

/**
 * RabbitMQ 队列初始化器（通过 HTTP Management API）
 *
 * <p>由于 {@code listener.direct.auto-startup: false}，所有 {@code @RabbitListener}
 * 容器在应用启动时不会自动启动，因此不会与队列发生交互。</p>
 *
 * <p>该组件监听 {@link ApplicationReadyEvent}，在应用完全就绪后通过
 * RabbitMQ HTTP Management API 执行以下操作：</p>
 * <ol>
 *   <li>删除所有业务队列（清除可能存在的旧队列，它们可能没有 DLX 参数）</li>
 *   <li>重建队列，确保携带正确的 {@code x-dead-letter-exchange} 参数</li>
 *   <li>绑定队列到对应的交换机</li>
 *   <li>启动所有 {@code @RabbitListener} 容器</li>
 * </ol>
 *
 * <p>此方式避免了监听器容器在启动期间因队列参数不符而抛出 PRECONDITION_FAILED。</p>
 */
@Slf4j
@Component
public class RabbitQueueInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final String API_BASE = "http://localhost:15672/api";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString("guest:guest".getBytes());

    private static final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    /** (队列名, 交换机, 路由键) */
    private static final List<QueueDef> QUEUES = List.of(
            new QueueDef(RabbitMQConfig.QUEUE_USER_REGISTER,  RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_USER_REGISTER),
            new QueueDef(RabbitMQConfig.QUEUE_USER_LOGIN,     RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_USER_LOGIN),
            new QueueDef(RabbitMQConfig.QUEUE_POINTS_REWARD,  RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_POINTS_REWARD),
            new QueueDef(RabbitMQConfig.QUEUE_NOTIFICATION,   RabbitMQConfig.EXCHANGE_TOPIC,  RabbitMQConfig.ROUTING_KEY_NOTIFICATION),
            new QueueDef(RabbitMQConfig.QUEUE_PURCHASE,       RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_PURCHASE),
            new QueueDef(RabbitMQConfig.QUEUE_USER_ACCOUNT,   RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_USER_ACCOUNT),
            new QueueDef(RabbitMQConfig.QUEUE_SECKILL,        RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_SECKILL),
            new QueueDef(RabbitMQConfig.USER_MESSAGE_QUEUE,   RabbitMQConfig.USER_MESSAGE_EXCHANGE, RabbitMQConfig.USER_MESSAGE_ROUTING_KEY),
            new QueueDef(RabbitMQConfig.QUEUE_POINTS_DEDUCT,  RabbitMQConfig.EXCHANGE_DIRECT, RabbitMQConfig.ROUTING_KEY_POINTS_DEDUCT)
    );

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("🚀 [RabbitMQ] 应用已就绪，通过 HTTP API 创建业务队列（含 x-dead-letter-exchange）...");

        boolean allSuccess = true;
        for (QueueDef qd : QUEUES) {
            try {
                // 1. 删除已存在的队列（确保不会有旧的、无 DLX 参数的队列残留）
                deleteQueue(qd.name);

                // 2. 创建队列（含死信交换机参数）
                createQueue(qd.name);

                // 3. 绑定到交换机
                createBinding(qd.name, qd.exchange, qd.routingKey);

                log.debug("  ✅ 队列 {} 创建并绑定 → {} [{}]", qd.name, qd.exchange, qd.routingKey);
            } catch (Exception e) {
                log.error("  ❌ 队列 {} 创建失败: {}", qd.name, e.getMessage());
                allSuccess = false;
            }
        }

        if (allSuccess) {
            log.info("✅ [RabbitMQ] 所有业务队列创建完成，启动监听器容器...");
        } else {
            log.warn("⚠️ [RabbitMQ] 部分队列创建异常，仍然启动监听器容器（可能抛出 PRECONDITION_FAILED）...");
        }

        // 启动所有 @RabbitListener 容器（DirectMessageListenerContainer）
        // 此时业务队列已具备正确的 x-dead-letter-exchange 参数，监听器不会再触发 PRECONDITION_FAILED
        listenerRegistry.start();
    }

    private void deleteQueue(String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/queues/%2F/" + encodePath(name)))
                .header("Authorization", AUTH_HEADER)
                .DELETE()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        // 204=成功, 404=不存在（无需处理）
        if (resp.statusCode() != 204 && resp.statusCode() != 404) {
            log.warn("  ⚠️ 删除队列 {} 返回 {}", name, resp.statusCode());
        }
    }

    private void createQueue(String name) throws Exception {
        String body = String.format(
                "{\"durable\":true,\"auto_delete\":false,\"arguments\":{\"x-dead-letter-exchange\":\"%s\"}}",
                RabbitMQConfig.EXCHANGE_DLX);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/queues/%2F/" + encodePath(name)))
                .header("Authorization", AUTH_HEADER)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 201 && resp.statusCode() != 204) {
            throw new RuntimeException("创建队列失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private void createBinding(String queue, String exchange, String routingKey) throws Exception {
        String body = String.format(
                "{\"routing_key\":\"%s\",\"arguments\":{}}", routingKey);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/bindings/%2F/e/" + encodePath(exchange) + "/q/" + encodePath(queue)))
                .header("Authorization", AUTH_HEADER)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 201 && resp.statusCode() != 204) {
            log.warn("  ⚠️ 绑定队列 {} → {} [{}] 返回 {}", queue, exchange, routingKey, resp.statusCode());
        }
    }

    private static String encodePath(String raw) {
        // RabbitMQ HTTP API 需要百分号编码路径段（特别是点号）
        return raw.replace(".", "%2E");
    }

    private record QueueDef(String name, String exchange, String routingKey) {}
}
