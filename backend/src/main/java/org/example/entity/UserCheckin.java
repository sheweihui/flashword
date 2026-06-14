package org.example.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户签到记录实体类
 */
@Data
public class UserCheckin {
    
    /**
     * 签到ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 签到日期
     */
    private LocalDate checkinDate;
    
    /**
     * 连续签到天数
     */
    private Integer continuousDays;
    
    /**
     * 获得的积分
     */
    private Integer pointsEarned;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
