package org.example.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.entity.SlowQueryLog;
import org.example.mapper.SlowQueryLogMapper;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DbTimeAspect {

    private final SlowQueryLogMapper slowQueryLogMapper;

    // ============================================================
    //  可调阈值（毫秒）
    // ============================================================

    /** 控制台黄色警告阈值：超过此值在日志输出 ⚠️ */
    private static final long CONSOLE_WARN_THRESHOLD = 200;
    /** 控制台红色警告阈值：超过此值在日志输出 🚨 */
    private static final long CONSOLE_ALERT_THRESHOLD = 500;
    /** 数据库记录阈值：超过此值时额外写入 slow_query_log 表 */
    private static final long DB_LOG_THRESHOLD = 500;

    // 颜色代码（ANSI 编码）
    private static final String YELLOW = "[33m";
    private static final String RED = "[31m";
    private static final String RESET = "[0m";

    /**
     * 拦截所有 Mapper 方法，统计耗时并根据阈值分级处理。
     * <p>
     * 排除 {@link SlowQueryLogMapper} 自身，避免 insert 操作触发递归记录。
     */
    @Around("execution(* org.example.mapper..*.*(..)) "
          + "&& !execution(* org.example.mapper.SlowQueryLogMapper.*(..))")
    public Object countDbTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long cost = System.currentTimeMillis() - start;

        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String methodFull = className + "." + methodName;
        String resultType = result != null ? result.getClass().getSimpleName() : "null";

        // ============================================================
        //  1. 控制台日志（沿用原有分级逻辑）
        // ============================================================
        if (cost > CONSOLE_ALERT_THRESHOLD) {
            log.info("\n{}━━━━━━━━━ [慢SQL警告] ━━━━━━━━━" + RESET, RED);
            log.info("{}⚠️  方法: {}" + RESET, RED, methodFull);
            log.info("{}⏱  耗时: {} ms" + RESET, RED, cost);
            log.info("{}🔍 返回: {}" + RESET, RED, resultType);
            log.info("{}━━━━━━━━━━━━━━━━━━━━━━━" + RESET + "\n", RED);
        } else if (cost > CONSOLE_WARN_THRESHOLD) {
            log.info("\n{}━━━━ [数据库操作] ━━━━" + RESET, YELLOW);
            log.info("{}📊 方法: {}" + RESET, YELLOW, methodFull);
            log.info("{}⏱  耗时: {} ms" + RESET, YELLOW, cost);
            log.info("{}━━━━━━━━━━━━━━━━" + RESET + "\n", YELLOW);
        }

        // ============================================================
        //  2. 数据库持久化（超过 DB_LOG_THRESHOLD 时写入）
        // ============================================================
        if (cost > DB_LOG_THRESHOLD) {
            insertSlowQueryLog(methodFull, cost, DB_LOG_THRESHOLD, resultType);
        }

        return result;
    }

    /**
     * 异步写入慢查询日志到数据库
     * <p>
     * 单独抽取为方法，方便后续改为 {@link org.springframework.scheduling.annotation.Async} 异步执行。
     */
    private void insertSlowQueryLog(String methodFull, long cost, long threshold, String resultType) {
        try {
            SlowQueryLog log = new SlowQueryLog();
            log.setMethodName(methodFull);
            log.setCostMs(cost);
            log.setThresholdMs(threshold);
            log.setResultType(resultType);
            slowQueryLogMapper.insert(log);
        } catch (Exception e) {
            // 日志记录本身失败不应影响主流程，仅打印警告
            log.warn("⚠️ [慢查询日志] 写入数据库失败 | method: {} | cost: {}ms | error: {}",
                    methodFull, cost, e.getMessage());
        }
    }
}
