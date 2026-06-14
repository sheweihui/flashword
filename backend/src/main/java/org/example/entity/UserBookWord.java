package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户单词书-单词关联实体类
 */
@Data
public class UserBookWord {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户单词书ID
     */
    private Long bookId;
    
    /**
     * 用户单词ID
     */
    private Long wordId;
    
    /**
     * 是否掌握：0-未掌握，1-已掌握
     */
    private Integer mastered;
    
    /**
     * 复习次数
     */
    private Integer reviewCount;
    
    /**
     * 最后复习时间
     */
    private LocalDateTime lastReviewTime;
    
    /**
     * 难度等级：1-简单，2-中等，3-困难
     */
    private Integer difficulty;
    
    /**
     * 学习优先级
     */
    private Integer priority;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
