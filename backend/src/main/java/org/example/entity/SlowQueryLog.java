package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 慢查询日志实体类
 * <p>
 * 由 {@link org.example.aspect.DbTimeAspect} 自动记录，
 * 当 Mapper 方法执行耗时超过阈值时写入此表，用于后续分析。
 */
@Data
public class SlowQueryLog {

    private Long id;
    /** 方法全名，如 "UserMapper.selectById" */
    private String methodName;
    /** 实际耗时（毫秒） */
    private Long costMs;
    /** 触发记录的阈值（毫秒），默认 500 */
    private Long thresholdMs;
    /** 返回类型，如 "User"、"List"、"Integer"、null 等 */
    private String resultType;
    /** 记录时间 */
    private LocalDateTime createdAt;
}
