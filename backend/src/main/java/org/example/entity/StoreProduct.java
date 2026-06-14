package org.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商店商品实体类
 */
@Data
public class StoreProduct {
    
    /**
     * 商品ID
     */
    private Long id;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品类型：1-单词书
     */
    private Integer productType;
    
    /**
     * 关联ID（如公共单词书ID）
     */
    private Long referenceId;
    
    /**
     * 商品描述
     */
    private String description;
    
    /**
     * 封面图片URL
     */
    private String coverImage;
    
    /**
     * 价格（积分）
     */
    private Integer price;
    
    /**
     * 原价（积分），用于显示折扣
     */
    private Integer originalPrice;
    
    /**
     * 是否热门：0-否，1-是
     */
    private Integer isHot;
    
    /**
     * 是否新品：0-否，1-是
     */
    private Integer isNew;
    
    /**
     * 是否推荐：0-否，1-是
     */
    private Integer isRecommended;
    
    /**
     * 排序权重（越大越靠前）
     */
    private Integer sortOrder;
    
    /**
     * 状态：0-下架，1-上架
     */
    private Integer status;
    
    /**
     * 库存（-1表示无限）
     */
    private Integer stock;
    
    /**
     * 销售数量
     */
    private Integer salesCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
