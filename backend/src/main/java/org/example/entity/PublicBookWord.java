package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共单词书-单词关联实体类
 */
@Data
public class PublicBookWord {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 公共单词书ID
     */
    private Long bookId;
    
    /**
     * 公共单词ID
     */
    private Long wordId;
    
    /**
     * 单词在书中的排序
     */
    private Integer sortOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
