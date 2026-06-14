package org.example.task;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;
import org.example.constant.RedisKeys;
import org.example.entity.SeckillActivity;
import org.example.entity.SeckillMessageLog;
import org.example.entity.SeckillOrder;
import org.example.mapper.SeckillActivityMapper;
import org.example.mapper.SeckillMessageLogMapper;
import org.example.mapper.SeckillOrderMapper;
import org.example.utils.RedisUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存定时同步任务
 * <p>
 * 定时将 MySQL 中的秒杀库存同步到 Redis，确保 Redis 和数据库长期一致。
 * 适用场景：
 * <ul>
 *   <li>Redis 因宕机/重启导致库存数据丢失</li>
 *   <li>MySQL 库存被手动调整但 Redis 未更新</li>
 *   <li>边缘情况下 Redis DECR 与 MySQL 乐观锁产生偏差</li>
 * </ul>
 * <p>
 * 同步方向：MySQL → Redis（以数据库为准）
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SeckillStockSyncTask {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillMessageLogMapper seckillMessageLogMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RedisUtil redisUtil;

    /** 消息最大重试次数 */
    private static final int MAX_RETRY = 5;
    /** 消息批量重发每次最多处理条数 */
    private static final int BATCH_SIZE = 50;

    // ============================================================
    //  定时任务
    // ============================================================

    /**
     * 每小时整点同步一次：将进行中/即将开始的秒杀活动库存从 MySQL 推送到 Redis
     * <p>
     * cron = "0 0 * * * *" = 每小时的第 0 分钟执行
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncStockHourly() {
        log.info("⏰ [库存同步-定时] 开始每小时库存同步...");
        int synced = syncActiveActivities();
        log.info("✅ [库存同步-定时] 完成 | 共同步 {} 个活动", synced);
    }

    /**
     * 每分钟检查一次：是否有刚结束的秒杀活动，强制同步一次库存
     * <p>
     * 秒杀刚结束时用户还在查询结果，此时库存应是最新状态
     */
    @Scheduled(fixedRate = 60_000)
    public void syncEndedActivities() {
        List<SeckillActivity> activities = seckillActivityMapper.selectAllActivities();
        if (activities == null || activities.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (SeckillActivity activity : activities) {
            // 活动结束 5 分钟内，强制同步一次
            if (activity.getEndTime() != null
                    && activity.getEndTime().isBefore(now)
                    && activity.getEndTime().isAfter(now.minusMinutes(5))) {
                syncSingleActivity(activity);
            }
        }
    }

    // ============================================================
    //  消息重试：扫表重新投递
    // ============================================================

    /**
     * 每 30 秒扫描一次 seckill_message_log，将 status=0（发送中）或 status=2（失败）
     * 且未超过重试上限的消息重新投递到 MQ。
     * <p>
     * 解决 "Redis 预扣成功、MySQL 已落单、但 MQ 消息发送失败" 的分布式事务问题。
     */
    @Scheduled(fixedRate = 30_000)
    public void retryFailedMessages() {
        // 查 status=0（发送中但可能丢失）和 status=2（显式失败）的消息
        List<SeckillMessageLog> pendingLogs = seckillMessageLogMapper.selectByStatus(0, MAX_RETRY);
        List<SeckillMessageLog> failedLogs = seckillMessageLogMapper.selectByStatus(2, MAX_RETRY);

        int retried = 0;
        for (SeckillMessageLog mlog : pendingLogs) {
            if (resendMessage(mlog)) {
                retried++;
            }
        }
        for (SeckillMessageLog mlog : failedLogs) {
            if (resendMessage(mlog)) {
                retried++;
            }
        }
        if (retried > 0) {
            log.info("⏰ [消息重试] 本轮重投完成 | 成功: {} 条", retried);
        }
    }

    /**
     * 重新投递单条消息
     */
    private boolean resendMessage(SeckillMessageLog msgLog) {
        try {
            // 解析 messageContent: "userId=1,activityId=2,price=10,productId=3"
            Map<String, Object> msg = parseMessageContent(msgLog.getMessageContent());
            if (msg == null) {
                log.warn("⚠️ [消息重试] 无法解析消息内容, 标记失败 | messageId: {}", msgLog.getMessageId());
                seckillMessageLogMapper.setErrorMessage(msgLog.getMessageId(), "无法解析消息内容");
                return false;
            }

            // 补充 orderNo
            msg.put("orderNo", msgLog.getMessageId());

            // 重新投递
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_SECKILL,
                    msg
            );

            // 更新重试次数 + 重置状态为 0（待消费）
            seckillMessageLogMapper.incrementRetryCount(msgLog.getMessageId());
            seckillMessageLogMapper.updateStatus(msgLog.getMessageId(), 0);

            log.info("✅ [消息重试] 重投成功 | messageId: {} | 已重试: {} 次",
                    msgLog.getMessageId(), msgLog.getRetryCount() + 1);
            return true;

        } catch (Exception e) {
            log.warn("⚠️ [消息重试] 重投失败 | messageId: {} | 原因: {}",
                    msgLog.getMessageId(), e.getMessage());
            seckillMessageLogMapper.incrementRetryCount(msgLog.getMessageId());
            return false;
        }
    }

    /**
     * 解析 "userId=1,activityId=2,price=10,productId=3" 格式的消息内容
     */
    private Map<String, Object> parseMessageContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = new HashMap<>();
            for (String pair : content.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    // 数字字段存 Long，其他字符串
                    String key = kv[0].trim();
                    String val = kv[1].trim();
                    if (val.matches("\\d+")) {
                        map.put(key, Long.parseLong(val));
                    } else {
                        map.put(key, val);
                    }
                }
            }
            return map.containsKey("userId") && map.containsKey("activityId") ? map : null;
        } catch (Exception e) {
            log.warn("⚠️ [消息重试] 解析消息内容失败 | content: {}", content, e);
            return null;
        }
    }

    // ============================================================
    //  订单补偿：从订单状态反查未完成的消息
    // ============================================================

    /**
     * 每 1 分钟扫描一次 seckill_order，发现 status=0 超过 5 分钟的订单，
     * 主动重新投递 MQ 消息（兜底补偿）。
     * <p>
     * 解决消息日志丢失、或消息发送失败但订单已落库的边缘情况。
     */
    @Scheduled(fixedRate = 60_000)
    public void compensatePendingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(5);
        List<SeckillOrder> pendingOrders = seckillOrderMapper.selectPendingBefore(deadline, 20);

        if (pendingOrders.isEmpty()) {
            return;
        }

        log.warn("⏰ [订单补偿] 发现 {} 个超时未处理的订单，准备重试", pendingOrders.size());
        for (SeckillOrder order : pendingOrders) {
            compensateSingleOrder(order);
        }
    }

    /**
     * 补偿单个超时订单
     */
    private void compensateSingleOrder(SeckillOrder order) {
        try {
            // 检查是否已经有对应的消息日志（可能只是 MQ 延迟）
            SeckillMessageLog existingLog = seckillMessageLogMapper.selectByMessageId(order.getOrderNo());
            if (existingLog != null && existingLog.getStatus() == 1) {
                // 消息已消费，订单状态可能没更新，直接修复
                seckillOrderMapper.updateStatus(order.getId(), 1);
                log.info("✅ [订单补偿] 订单已实际完成，修复状态 | orderNo: {}", order.getOrderNo());
                return;
            }

            // 如果没有消息日志，或日志未成功，重新投递
            if (existingLog == null) {
                // 重建消息日志
                SeckillMessageLog newLog = new SeckillMessageLog();
                newLog.setMessageId(order.getOrderNo());
                newLog.setMessageContent(String.format(
                        "userId=%d,activityId=%d", order.getUserId(), order.getActivityId()));
                newLog.setStatus(0);
                seckillMessageLogMapper.insert(newLog);
            }

            // 重新发送 MQ 消息
            Map<String, Object> msg = new HashMap<>();
            msg.put("userId", order.getUserId());
            msg.put("activityId", order.getActivityId());
            msg.put("orderNo", order.getOrderNo());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_SECKILL,
                    msg
            );

            log.info("✅ [订单补偿] 重新投递消息 | orderNo: {} | userId: {} | activityId: {}",
                    order.getOrderNo(), order.getUserId(), order.getActivityId());

        } catch (Exception e) {
            log.warn("⚠️ [订单补偿] 补偿失败 | orderNo: {} | 原因: {}", order.getOrderNo(), e.getMessage());
        }
    }

    // ============================================================
    //  系统启动时预热
    // ============================================================

    /**
     * 项目启动时，自动将未结束的秒杀活动库存同步到 Redis
     */
    @PostConstruct
    public void initSyncOnStartup() {
        log.info("🚀 [库存同步-启动] 系统启动，同步秒杀库存到 Redis...");
        int synced = syncActiveActivities();
        log.info("✅ [库存同步-启动] 完成 | 共同步 {} 个活动", synced);
    }

    // ============================================================
    //  核心同步方法
    // ============================================================

    /**
     * 同步所有进行中或即将开始的秒杀活动
     */
    private int syncActiveActivities() {
        List<SeckillActivity> activities = seckillActivityMapper.selectAllActivities();
        if (activities == null || activities.isEmpty()) {
            log.debug("⏰ [库存同步] 暂无秒杀活动需要同步");
            return 0;
        }

        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SeckillActivity activity : activities) {
            // 跳过已结束超过 1 小时的活动（不再需要 Redis 缓存）
            if (activity.getEndTime() != null && activity.getEndTime().isBefore(now.minusHours(1))) {
                continue;
            }
            syncSingleActivity(activity);
            count++;
        }
        return count;
    }

    /**
     * 同步单个活动的库存到 Redis
     */
    private void syncSingleActivity(SeckillActivity activity) {
        String stockKey = RedisKeys.seckillStock(activity.getId());
        Long dbStock = (long) (activity.getStock() != null ? activity.getStock() : 0);

        // 获取当前 Redis 库存用于对比
        Object redisStockObj = redisUtil.get(stockKey);
        long redisStock = redisStockObj != null ? ((Number) redisStockObj).longValue() : -1;

        if (redisStock != dbStock) {
            log.info("🔄 [库存同步] 活动ID: {} | MySQL: {} | Redis: {} → 同步至 {}",
                    activity.getId(), dbStock, redisStock == -1 ? "缺失" : redisStock, dbStock);
            redisUtil.set(stockKey, dbStock, 24, TimeUnit.HOURS);
        }
    }

    // ============================================================
    //  手动触发（可对外暴露）
    // ============================================================

    /**
     * 手动触发指定活动的库存同步
     *
     * @param activityId 秒杀活动ID
     */
    public void syncByActivityId(Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("⚠️ [库存同步-手动] 活动不存在 | ID: {}", activityId);
            return;
        }
        syncSingleActivity(activity);
        log.info("✅ [库存同步-手动] 活动ID: {} 同步完成", activityId);
    }

    /**
     * 手动触发所有活动同步
     */
    public void syncAll() {
        log.info("🔄 [库存同步-手动] 开始全量同步...");
        int count = syncActiveActivities();
        log.info("✅ [库存同步-手动] 全量同步完成 | 共 {} 个活动", count);
    }
}
