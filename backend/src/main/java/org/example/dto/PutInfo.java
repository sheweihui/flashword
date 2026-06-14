package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新单词书请求DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PutInfo {
    private String bookName;
    private String description;
}

