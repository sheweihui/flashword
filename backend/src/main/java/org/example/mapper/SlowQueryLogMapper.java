package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.entity.SlowQueryLog;

/**
 * 慢查询日志 Mapper 接口
 * <p>
 * 由 {@link org.example.aspect.DbTimeAspect} 在检测到慢 SQL 时写入。
 * 注意：此 Mapper 已被排除在 DbTimeAspect 的切面之外，避免递归记录。
 */
@Mapper
public interface SlowQueryLogMapper {

    /**
     * 插入一条慢查询日志
     */
    int insert(SlowQueryLog log);
}
