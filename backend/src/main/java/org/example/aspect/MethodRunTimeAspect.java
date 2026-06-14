package org.example.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.MethodRunTime;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class MethodRunTimeAspect {
    @Around("@annotation(org.example.annotation.MethodRunTime)")
    public Object countRunTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String value = signature.getMethod().getAnnotation(MethodRunTime.class).value();
        Object result = joinPoint.proceed();
        long cost = System.currentTimeMillis() - start;
        log.info("方法: {} | 耗时: {}ms", value, cost);
        return result;
    }
}
