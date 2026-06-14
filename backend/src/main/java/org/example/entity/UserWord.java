package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户单词实体类（生词本）
 */
@Data
public class UserWord {
    
    /**
     * 单词ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 单词文本
     */
    private String wordText;
    
    /**
     * 音标
     */
    private String phonetic;
    
    /**
     * 词性
     */
    private String partOfSpeech;
    
    /**
     * 中文释义
     */
    private String definition;
    
    /**
     * 例句
     */
    private String exampleSentence;
    
    /**
     * 例句翻译
     */
    private String exampleTranslation;
    
    /**
     * 发音音频URL
     */
    private String audioUrl;
    
    /**
     * 个人笔记
     */
    private String note;
    
    /**
     * 标签，逗号分隔
     */
    private String tags;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
