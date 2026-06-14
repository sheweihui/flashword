package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共单词实体类
 */
@Data
public class PublicWord {
    
    /**
     * 公共单词ID
     */
    private Long id;
    
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
     * 难度等级：1-简单，2-中等，3-困难
     */
    private Integer difficultyLevel;
    
    /**
     * 词频排名
     */
    private Integer frequencyRank;
    
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
