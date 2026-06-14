package org.example.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class RabbitTimeAspect {
    @Around("@annotation(org.example.annotation.RabbitTime)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        finally {
            long end = System.currentTimeMillis();
            log.info("⏱️ [异步消费者] 处理完成 | 耗时: {}ms", end - start);
        }
    }
}
