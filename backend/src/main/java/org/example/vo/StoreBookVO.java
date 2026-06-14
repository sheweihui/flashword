package org.example.vo;

import lombok.Data;

/**
 * 商店单词书VO
 */
@Data
public class StoreBookVO {
    
    /**
     * 单词书ID
     */
    private Long id;
    
    /**
     * 单词书名称
     */
    private String bookName;
    
    /**
     * 单词书描述
     */
    private String description;
    
    /**
     * 封面图片URL
     */
    private String coverImage;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 难度等级
     */
    private Integer difficulty;
    
    /**
     * 单词数量
     */
    private Integer wordCount;
    
    /**
     * 价格（积分）
     */
    private Integer price;
    
    /**
     * 原价（积分）
     */
    private Integer originalPrice;
    
    /**
     * 是否热门
     */
    private Integer isHot;
    
    /**
     * 是否新品
     */
    private Integer isNew;
    
    /**
     * 是否推荐
     */
    private Integer isRecommended;
    
    /**
     * 是否已购买
     */
    private Boolean isPurchased;
    
    /**
     * 折扣率（百分比）
     */
    private Double discount;

    /**
     * 销售数量
     */
    private Integer salesCount;
}
