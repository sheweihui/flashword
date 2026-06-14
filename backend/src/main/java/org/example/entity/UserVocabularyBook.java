package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户单词书实体类
 */
@Data
public class UserVocabularyBook {
    
    /**
     * 单词书ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
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
     * 单词数量
     */
    private Integer wordCount;
    
    /**
     * 是否公开：0-私有，1-公开
     */
    private Integer isPublic;
    
    /**
     * 来源类型：1-手动创建，2-从商店购买
     */
    private Integer sourceType;
    
    /**
     * 来源商店单词书ID（如果从商店购买）
     */
    private Long sourceStoreBookId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
