package org.example.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获 Controller 层异常，消除重复 try-catch
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public Result<?> handleRateLimitException(RateLimitException e) {
        log.warn("🚫 [限流拦截] {} | 类型: {}", e.getMessage(), e.getType());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.warn("⚠️ [业务异常] {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("❌ [系统异常]", e);
        return Result.error("服务器内部错误: " + e.getMessage());
    }
}
