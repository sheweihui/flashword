package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI服务配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {
    
    /**
     * DeepSeek API密钥
     */
    private String apiKey;
    
    /**
     * DeepSeek API基础URL
     */
    private String baseUrl = "https://api.deepseek.com/v1";
    
    /**
     * DeepSeek模型名称
     */
    private String model = "deepseek-chat";
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 30000;
    
    /**
     * 是否启用DeepSeek功能
     */
    private boolean enabled = false;
}
