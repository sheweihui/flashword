package org.example.service;

/**
 * 购买服务接口
 */
public interface PurchaseService {
    
    /**
     * 同步处理用户购买逻辑（直接数据库操作）
     * @param userId 用户ID
     * @param storeBookId 商店单词书ID
     * @return 用户单词书ID
     */
    Long purchaseBookSync(Long userId, Long storeBookId);
    
    /**
     * 异步处理用户购买逻辑（使用RabbitMQ处理非关键路径）
     * @param userId 用户ID
     * @param storeBookId 商店单词书ID
     * @return 用户单词书ID
     */
    Long purchaseBookAsync(Long userId, Long storeBookId);
}
