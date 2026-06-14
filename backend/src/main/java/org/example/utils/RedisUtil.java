package org.example.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 基础操作 ====================

    /**
     * 存数据（永久有效）
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 存数据（带过期时间）
     * @param time 过期时间
     * @param unit 时间单位（TimeUnit.SECONDS 等）
     */
    public void set(String key, Object value, long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, time, unit);
    }

    /**
     * 取数据
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 取数据（泛型方法，支持类型安全）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除数据
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断 key 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    // ==================== 扩展：常用场景 ====================

    /**
     * 给 key 设置过期时间
     */
    public Boolean expire(String key, long time, TimeUnit unit) {
        return redisTemplate.expire(key, time, unit);
    }

    /**
     * 给数值类型的 key 自增（比如计数器）
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 给数值类型的 key 自减（比如扣库存）
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }
    
    /**
     * 如果key不存在则设置值（用于防止重复提交）
     * @return true-设置成功，false-key已存在
     */
    public Boolean setIfAbsent(String key, Object value, long time, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, time, unit);
    }
}