package org.example.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户积分信息VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsVO {
    
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
}
