package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单词实体类
 */
@Data
public class Word {
    
    private Long id;
    
    private String wordText;
    
    private String phonetic;
    
    private String partOfSpeech;
    
    private String definition;
    
    private String exampleSentence;
    
    private String exampleTranslation;
    
    private String audioUrl;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
