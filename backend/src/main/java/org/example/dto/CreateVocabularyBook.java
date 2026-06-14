package org.example.dto;

import lombok.Data;

/**
 * 创建单词书请求DTO
 */
@Data
public class CreateVocabularyBook {
    private Long userId;
    private String bookName;
    private String description;
    private String coverImage;
}
