package org.example.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 分布式限流器 — 基于 Redis
 * <p>
 * 提供两种限流方式：
 * <ul>
 *   <li><b>用户级固定窗口</b> — 限制单个用户每秒请求数，防恶意刷接口</li>
 *   <li><b>全局令牌桶</b> — 限制系统总 QPS，保护后端不被冲垮</li>
 * </ul>
 * 使用 Lua 脚本保证原子性。
 * <p>
 * Key 规范遵循 {@code wf:rate:*}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 令牌桶 Lua 脚本 */
    private DefaultRedisScript<Long> tokenBucketScript;

    /** 固定窗口 Lua 脚本 */
    private DefaultRedisScript<Long> fixedWindowScript;

    public static final String PREFIX_GLOBAL_TOKEN  = "wf:rate:global:";
    public static final String PREFIX_USER_WINDOW   = "wf:rate:user:";

    @PostConstruct
    public void init() {
        // 令牌桶脚本
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        tokenBucketScript.setResultType(Long.class);

        // 固定窗口脚本
        fixedWindowScript = new DefaultRedisScript<>();
        fixedWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/fixed_window.lua")));
        fixedWindowScript.setResultType(Long.class);
    }

    // ============================================================
    //  用户级限流（固定窗口）
    // ============================================================

    /**
     * 用户级限流 — 固定窗口
     *
     * @param keySuffix  限流 key 后缀（如 {@code seckill:1001}）
     * @param maxRequests 窗口内允许的最大请求数
     * @param windowSec  窗口大小（秒）
     * @return true=通过，false=被限流
     */
    public boolean tryAcquirePerUser(String keySuffix, int maxRequests, int windowSec) {
        String key = PREFIX_USER_WINDOW + keySuffix;
        List<String> keys = List.of(key);
        Long result = redisTemplate.execute(fixedWindowScript, keys, maxRequests, windowSec);
        boolean pass = result != null && result == 1L;
        if (!pass) {
            log.warn("🚫 [限流-用户] 触发用户级限流 | key: {} | 阈值: {}/{}s", key, maxRequests, windowSec);
        }
        return pass;
    }

    /**
     * 用户级限流（秒杀场景快捷方法）
     * 默认：每秒最多 3 次
     */
    public boolean tryAcquireSeckillUser(Long userId, Long activityId) {
        return tryAcquirePerUser(
                String.format("seckill:%d:%d", userId, activityId),
                3, 1  // 每秒最多 3 次
        );
    }


    // ============================================================
    //  全局限流（令牌桶）
    // ============================================================

    /**
     * 全局令牌桶限流
     *
     * @param keySuffix  限流 key 后缀
     * @param capacity   桶容量（最大突发）
     * @param refillRate 每秒填充速率
     * @return true=通过，false=被限流
     */
    public boolean tryAcquireGlobal(String keySuffix, int capacity, int refillRate) {
        String key = PREFIX_GLOBAL_TOKEN + keySuffix;
        long now = System.currentTimeMillis() / 1000;
        List<String> keys = List.of(key);
        Long result = redisTemplate.execute(
                tokenBucketScript, keys, capacity, refillRate, now);
        boolean pass = result != null && result == 1L;
        if (!pass) {
            log.warn("🚫 [限流-全局] 触发全局限流 | key: {} | cap: {} | rate: {}/s", key, capacity, refillRate);
        }
        return pass;
    }

    /**
     * 全局限流（秒杀场景快捷方法）
     * 默认：容量 300，每秒填充 200（即稳定 QPS 200，突发 300）
     */
    public boolean tryAcquireSeckillGlobal() {
        return tryAcquireGlobal("seckill", 300, 200);
    }
}
