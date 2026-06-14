package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀消息日志实体类
 */
@Data
public class SeckillMessageLog {
    
    private Long id;
    private String messageId;
    private String messageContent;
    private Integer status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
}
