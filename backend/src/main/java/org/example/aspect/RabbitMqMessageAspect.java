package org.example.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.RabbitMqMessage;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ消息切面 - 记录消息来源和处理耗时
 */
@Slf4j
@Component
@Aspect
public class RabbitMqMessageAspect {
    
    @Around("@annotation(org.example.annotation.RabbitMqMessage)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String message = signature.getMethod().getAnnotation(RabbitMqMessage.class).message();
        
        long startTime = System.currentTimeMillis();
        log.info("📨 [RabbitMQ-开始] 处理消息: {}", message);
        
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - startTime;
            log.info("✅ [RabbitMQ-完成] 消息: {} | 耗时: {}ms", message, cost);
            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("❌ [RabbitMQ-失败] 消息: {} | 耗时: {}ms | 错误: {}", message, cost, e.getMessage(), e);
            throw e;
        }
    }
}
