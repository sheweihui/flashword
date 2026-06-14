package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除单词请求DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteInfo {
    private Long bookId;
    private Long wordId;
}
