package org.example.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.context.UserContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志切面 - 记录Controller层的方法调用日志
 */
@Aspect
@Component
@Slf4j
public class LogAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 定义切点：拦截所有Controller层的方法
     */
    @Pointcut("execution(* org.example.controller..*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：记录方法执行的完整过程
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        // 获取用户信息
        Long userId = UserContextHolder.getUserId();
        String formatTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis());
        // 记录请求日志 - 使用统一格式
        log.info("🚀 [REQUEST] {} {} | 用户: {}",
                request != null ? request.getMethod() : "UNKNOWN",
                request != null ? request.getRequestURI() : "UNKNOWN",
                userId != null ? userId : "未登录");
        log.debug("   ├─ 类名: {}.{}", className, methodName);
        log.debug("   ├─ 时间: {}", formatTime);
        
        // 记录请求参数
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();
        if (paramNames != null && paramNames.length > 0) {
            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                // 跳过HttpServletRequest和HttpServletResponse等大对象
                if (args[i] instanceof HttpServletRequest || 
                    args[i] instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                }
                params.put(paramNames[i], args[i]);
            }
            if (!params.isEmpty()) {
                try {
                    String paramsJson = objectMapper.writeValueAsString(params);
                    log.debug("   └─ 参数: {}", paramsJson.length() > 200 ? paramsJson.substring(0, 200) + "..." : paramsJson);
                } catch (Exception e) {
                    log.warn("   └─ 参数序列化失败: {}", e.getMessage());
                }
            }
        }
        
        Object result;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录响应日志 - 根据耗时分级
            if (executionTime > 1000) {
                log.warn("⚠️  [SLOW] {}ms | {} {}", executionTime,
                        request != null ? request.getMethod() : "UNKNOWN",
                        request != null ? request.getRequestURI() : "UNKNOWN");
            } else {
                log.debug("✅ [SUCCESS] {}ms | {} {}", executionTime,
                        request != null ? request.getMethod() : "UNKNOWN",
                        request != null ? request.getRequestURI() : "UNKNOWN");
            }
            log.trace("   └─ 响应: {}", formatResult(result));
            
            return result;
            
        } catch (Throwable throwable) {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录异常日志
            log.error("执行耗时: {} ms", executionTime);
            log.error("异常信息: {}", throwable.getMessage());
            log.error("异常类型: {}", throwable.getClass().getName());
            log.error("========== 请求失败 ==========\n", throwable);
            
            throw throwable;
        }
    }

    /**
     * 格式化响应结果（避免输出过大的数据）
     */
    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        
        try {
            String json = objectMapper.writeValueAsString(result);
            // 如果结果太长，只保留前500个字符
            if (json.length() > 500) {
                return json.substring(0, 500) + "... (内容过长已截断)";
            }
            return json;
        } catch (Exception e) {
            return result.toString();
        }
    }

}
