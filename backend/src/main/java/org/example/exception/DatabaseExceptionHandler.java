package org.example.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * 全局数据库异常处理器
 * 专门捕获和记录 MySQL 执行失败的详细日志
 */
@Slf4j
@RestControllerAdvice
public class DatabaseExceptionHandler {

    /**
     * 处理重复键异常（唯一约束冲突）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public org.example.common.Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("❌ [DB-唯一约束] 数据重复 | 错误: {}", e.getMessage());
        log.error("❌ [DB-唯一约束] 完整堆栈:", e);
        
        // 提取更友好的错误信息
        String message = "数据已存在，请勿重复操作";
        if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
            // 提取重复的字段值
            String[] parts = e.getMessage().split("'");
            if (parts.length >= 2) {
                message = "数据 '" + parts[1] + "' 已存在";
            }
        }
        
        return org.example.common.Result.error(message);
    }

    /**
     * 处理 SQL 语法错误
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public org.example.common.Result<?> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("❌ [DB-SQL语法] SQL语句错误 | 错误: {}", e.getMessage());
        log.error("❌ [DB-SQL语法] 完整堆栈:", e);
        
        // 记录详细的 SQL 错误信息
        SQLException sqlEx = e.getSQLException();
        if (sqlEx != null) {
            log.error("❌ [DB-SQL语法] SQL状态码: {}", sqlEx.getSQLState());
            log.error("❌ [DB-SQL语法] 错误代码: {}", sqlEx.getErrorCode());
            log.error("❌ [DB-SQL语法] 错误消息: {}", sqlEx.getMessage());
        }
        
        return org.example.common.Result.error("SQL语句错误，请联系管理员");
    }

    /**
     * 处理未分类的 SQL 异常
     */
    @ExceptionHandler(UncategorizedSQLException.class)
    public org.example.common.Result<?> handleUncategorizedSQLException(UncategorizedSQLException e) {
        log.error("❌ [DB-未分类] 数据库异常 | 错误: {}", e.getMessage());
        log.error("❌ [DB-未分类] 完整堆栈:", e);
        
        SQLException sqlEx = e.getSQLException();
        if (sqlEx != null) {
            log.error("❌ [DB-未分类] SQL状态码: {}", sqlEx.getSQLState());
            log.error("❌ [DB-未分类] 错误代码: {}", sqlEx.getErrorCode());
            log.error("❌ [DB-未分类] 错误消息: {}", sqlEx.getMessage());
            
            // 常见错误代码映射
            switch (sqlEx.getErrorCode()) {
                case 1054:
                    log.error("❌ [DB-错误代码1054] 字段不存在");
                    break;
                case 1062:
                    log.error("❌ [DB-错误代码1062] 唯一约束冲突");
                    break;
                case 1064:
                    log.error("❌ [DB-错误代码1064] SQL语法错误");
                    break;
                case 1146:
                    log.error("❌ [DB-错误代码1146] 表不存在");
                    break;
                case 1452:
                    log.error("❌ [DB-错误代码1452] 外键约束失败");
                    break;
                default:
                    log.error("❌ [DB-未知错误代码] {}", sqlEx.getErrorCode());
            }
        }
        
        return org.example.common.Result.error("数据库操作失败: " + e.getMessage());
    }

    /**
     * 处理通用数据访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public org.example.common.Result<?> handleDataAccessException(DataAccessException e) {
        log.error("❌ [DB-数据访问] 数据访问异常 | 错误: {}", e.getMessage());
        log.error("❌ [DB-数据访问] 完整堆栈:", e);
        
        // 如果有底层 SQL 异常，记录详细信息
        Throwable cause = e.getCause();
        if (cause instanceof SQLException) {
            SQLException sqlEx = (SQLException) cause;
            log.error("❌ [DB-数据访问] SQL状态码: {}", sqlEx.getSQLState());
            log.error("❌ [DB-数据访问] 错误代码: {}", sqlEx.getErrorCode());
            log.error("❌ [DB-数据访问] 错误消息: {}", sqlEx.getMessage());
        }
        
        return org.example.common.Result.error("数据访问失败: " + e.getMessage());
    }

    /**
     * 处理通用 SQL 异常
     */
    @ExceptionHandler(SQLException.class)
    public org.example.common.Result<?> handleSQLException(SQLException e) {
        log.error("❌ [DB-SQL] SQL执行异常 | 错误: {}", e.getMessage());
        log.error("❌ [DB-SQL] 完整堆栈:", e);
        log.error("❌ [DB-SQL] SQL状态码: {}", e.getSQLState());
        log.error("❌ [DB-SQL] 错误代码: {}", e.getErrorCode());
        
        return org.example.common.Result.error("SQL执行失败: " + e.getMessage());
    }
}
