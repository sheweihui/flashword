package org.example.vo;

import lombok.Data;

/**
 * 学习统计 VO
 */
@Data
public class StudyStatsVO {

    /** 总单词数 */
    private Integer totalWords;

    /** 已掌握单词数 */
    private Integer masteredWords;

    /** 掌握率 */
    private String masteryRate;

    /** 今日学习单词数 */
    private Integer todayLearned;

    /** 今日复习单词数 */
    private Integer todayReviewed;

    /** 连续学习天数 */
    private Integer streakDays;
}
