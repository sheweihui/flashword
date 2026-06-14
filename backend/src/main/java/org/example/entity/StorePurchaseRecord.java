package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商店购买记录实体类
 */
@Data
public class StorePurchaseRecord {
    
    /**
     * 购买记录ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 购买时支付的价格
     */
    private Integer pricePaid;
    
    /**
     * 购买类型：1-正常购买，2-免费领取，3-VIP赠送
     */
    private Integer purchaseType;
    
    /**
     * 购买后生成的用户单词书ID
     */
    private Long userBookId;
    
    /**
     * 购买时间
     */
    private LocalDateTime createdAt;
}
