package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户积分交易记录实体类
 */
@Data
public class UserPointsTransaction {
    
    /**
     * 交易ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易类型：1-注册赠送，2-每日签到，3-学习奖励，4-购买消费，5-系统调整
     */
    private Integer type;
    
    /**
     * 积分变化量（正数为增加，负数为减少）
     */
    private Integer amount;
    
    /**
     * 交易后余额
     */
    private Integer balanceAfter;
    
    /**
     * 交易描述
     */
    private String description;
    
    /**
     * 关联ID（如购买的单词书ID）
     */
    private Long referenceId;

    /**
     * 幂等性标识（如订单号、消息ID等）
     */
    private String idempotencyKey;
    
    /**
     * 交易时间
     */
    private LocalDateTime createdAt;
}
