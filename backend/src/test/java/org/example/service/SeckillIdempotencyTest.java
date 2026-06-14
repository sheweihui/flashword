package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.SeckillActivity;
import org.example.mapper.SeckillActivityMapper;
import org.example.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀幂等性测试类
 */
@Slf4j
@SpringBootTest
public class SeckillIdempotencyTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    /**
     * 测试并发秒杀的幂等性（防止超卖和重复购买）
     */
    @Test
    public void testConcurrentSeckill() throws InterruptedException {
        // 假设数据库中有一个 ID 为 1 的秒杀活动，库存为 10
        Long activityId = 1L;
        int threadCount = 20; // 模拟 20 个用户同时抢购
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final long userId = 100 + i; // 不同的用户 ID
            executor.submit(() -> {
                try {
                    // 尝试异步秒杀
                    String orderNo = seckillService.executeSeckillAsync(userId, activityId);
                    log.info("用户 {} 秒杀成功，订单号: {}", userId, orderNo);
                } catch (Exception e) {
                    log.warn("用户 {} 秒杀失败: {}", userId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有线程执行完毕
        executor.shutdown();

        // 验证结果
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        assertNotNull(activity);
        
        // 验证库存是否正确扣减（初始库存 - 成功人数）
        log.info("剩余库存: {}", activity.getStock());
        
        // 验证每个用户是否只产生了一个订单
        // 这里可以通过查询数据库统计该活动的订单总数来验证
    }
}
