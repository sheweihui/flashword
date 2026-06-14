package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SeckillOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀订单Mapper接口
 */
@Mapper
public interface SeckillOrderMapper {

    /**
     * 插入秒杀订单
     */
    int insert(SeckillOrder order);

    /**
     * 根据用户ID和活动ID查询订单（防止重复购买）
     */
    SeckillOrder selectByUserAndActivity(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /**
     * 查询处理中超时的订单（status=0 且创建时间早于指定时间），用于补偿重试
     */
    List<SeckillOrder> selectPendingBefore(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 根据订单号查询订单
     */
    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据订单号更新订单状态
     */
    int updateStatusByOrderNo(@Param("orderNo") String orderNo, @Param("status") Integer status);
}
