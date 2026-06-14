package org.example.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀商品DTO
 */
@Data
public class FlashSaleDTO {
    
    /**
     * 秒杀活动ID
     */
    private Long id;
    
    /**
     * 单词书ID（商品关联的单词书ID）
     */
    private Long bookId;
    
    /**
     * 单词书名称
     */
    private String bookName;
    
    /**
     * 封面图片
     */
    private String coverImage;
    
    /**
     * 原价（积分）
     */
    private Integer originalPrice;
    
    /**
     * 秒杀价（积分）
     */
    private Integer flashPrice;
    
    /**
     * 库存
     */
    private Integer stock;
    
    /**
     * 已售数量
     */
    private Integer soldCount;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 状态：upcoming-未开始，ongoing-进行中，ended-已结束
     */
    private String status;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 单词数量
     */
    private Integer wordCount;
    
    /**
     * 难度等级：1-初级，2-中级，3-高级
     */
    private Integer difficulty;
}
