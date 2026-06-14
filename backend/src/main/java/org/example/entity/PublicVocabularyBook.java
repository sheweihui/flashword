package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共单词书实体类
 */
@Data
public class PublicVocabularyBook {
    
    /**
     * 单词书ID
     */
    private Long id;
    
    /**
     * 单词书名称
     */
    private String bookName;
    
    /**
     * 单词书描述
     */
    private String description;
    
    /**
     * 封面图片URL
     */
    private String coverImage;
    
    /**
     * 分类：cet4-四级，cet6-六级，ielts-雅思，toefl-托福，business-商务，daily-日常
     */
    private String category;
    
    /**
     * 难度等级：1-初级，2-中级，3-高级
     */
    private Integer difficulty;
    
    /**
     * 单词数量
     */
    private Integer wordCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
