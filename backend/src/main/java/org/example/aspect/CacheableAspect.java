package org.example.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.Cacheable;
import org.example.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存读取切面
 * 拦截 @Cacheable 注解的方法，实现先查缓存，缓存未命中再执行方法并写入缓存的逻辑
 */
@Slf4j
@Aspect
@Component
public class CacheableAspect {

    @Autowired
    private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(cacheable)")
    public Object cacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        // 1. 解析缓存Key
        String key = parseKey(joinPoint, cacheable.key());
        
        // 2. 尝试从缓存获取数据
        log.debug("🔍 [Redis] 尝试从缓存获取数据 | Key: {}", key);
        Object cachedValue = redisUtil.get(key);
        
        if (cachedValue != null) {
            log.info("✅ [Redis] 缓存命中 | Key: {}", key);
            return cachedValue;
        }
        
        log.info("💾 [DB] 缓存未命中，从数据库查询 | Key: {}", key);
        
        // 3. 缓存未命中，执行目标方法
        Object result = joinPoint.proceed();
        
        // 4. 将结果写入缓存
        if (result != null) {
            long expireTime = cacheable.expire();
            redisUtil.set(key, result, expireTime, TimeUnit.SECONDS);
            log.info("✅ [DB→Redis] 数据已缓存 | Key: {} | 过期时间: {}秒", key, expireTime);
        }
        
        return result;
    }

    /**
     * 解析SpEL表达式生成缓存Key
     */
    private String parseKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }

            return parser.parseExpression(keyExpression).getValue(context, String.class);
        } catch (Exception e) {
            log.error("❌ 解析缓存Key失败 | 表达式: {}", keyExpression, e);
            // 如果解析失败，返回原始表达式作为Key
            return keyExpression;
        }
    }
}
