package org.example.dto;

import lombok.Data;

/**
 * AI单词信息响应DTO
 */
@Data
public class WordDTO {
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 单词本ID
     */
    private Long bookId;
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
     * 音频URL
     */
    private String audioUrl;
}
