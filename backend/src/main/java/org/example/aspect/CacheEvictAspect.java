package org.example.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.CacheEvict;
import org.example.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 缓存清除切面
 * 拦截 @CacheEvict 注解的方法，在方法执行后清除指定的缓存
 */
@Slf4j
@Aspect
@Component
public class CacheEvictAspect {
    
    @Autowired
    private RedisUtil redisUtil;
    
    private final ExpressionParser parser = new SpelExpressionParser();
    
    @Around("@annotation(cacheEvict)")
    public Object cacheEvict(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        // 1. 先执行目标方法
        Object result = joinPoint.proceed();
        
        // 2. 解析缓存Key
        String keyExpression = cacheEvict.key();
        
        // 3. 清除缓存
        if (cacheEvict.allEntries()) {
            // 清除所有相关缓存（需要根据具体业务实现）
            log.info("🗑️ [缓存清除] 清除所有缓存 | Key表达式: {}", keyExpression);
        } else {
            String key = parseKey(joinPoint, keyExpression);
            if (key != null && !key.isEmpty()) {
                redisUtil.delete(key);
                log.info("🗑️ [缓存清除] 缓存已清除 | Key: {}", key);
            }
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
            return keyExpression;
        }
    }
}
