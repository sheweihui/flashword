package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SeckillActivity;

import java.util.List;

/**
 * 秒杀活动Mapper接口
 */
@Mapper
public interface SeckillActivityMapper {
    
    /**
     * 根据ID查询秒杀活动
     */
    SeckillActivity selectById(@Param("id") Long id);
    
    /**
     * 扣减库存（乐观锁）
     */
    int deductStock(@Param("id") Long id);
    
    /**
     * 查询所有秒杀活动（按开始时间排序）
     */
    List<SeckillActivity> selectAllActivities();
}
