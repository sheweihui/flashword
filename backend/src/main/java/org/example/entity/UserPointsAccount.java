package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户积分账户实体类
 */
@Data
public class UserPointsAccount {
    
    /**
     * 账户ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 当前积分余额
     */
    private Integer balance;
    
    /**
     * 累计获得积分
     */
    private Integer totalEarned;
    
    /**
     * 累计消费积分
     */
    private Integer totalSpent;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
