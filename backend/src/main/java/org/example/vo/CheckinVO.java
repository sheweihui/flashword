package org.example.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 签到结果VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckinVO {
    
    /**
     * 是否签到成功
     */
    private Boolean checkedIn;
    
    /**
     * 获得的积分
     */
    private Integer pointsEarned;
    
    /**
     * 连续签到天数
     */
    private Integer continuousDays;
    
    /**
     * 额外奖励积分
     */
    private Integer bonusPoints;
    
    /**
     * 签到日期
     */
    private LocalDate checkinDate;
    
    /**
     * 消息提示
     */
    private String message;
    /**
     * 折扣
     */
    private Double discount;
    /**
     * 单词数量
     */
    private Integer wordCount;
    /**
     * 分类
     */
    private String category;
}
