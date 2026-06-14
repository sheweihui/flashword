package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体类
 */
@Data
public class SeckillOrder {

    /** 订单ID */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 秒杀活动ID */
    private Long activityId;
    /** 订单编号 */
    private String orderNo;
    /** 订单状态：0-处理中（MQ 尚未消费），1-已完成（MQ 消费成功），2-异常（重试耗尽） */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
