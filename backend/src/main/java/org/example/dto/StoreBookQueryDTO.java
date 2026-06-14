package org.example.dto;

import lombok.Data;

/**
 * 商店单词书查询DTO
 */
@Data
public class StoreBookQueryDTO {
    
    /**
     * 分类筛选：cet4, cet6, ielts, toefl, business, daily
     */
    private String category;
    
    /**
     * 难度筛选：1-初级，2-中级，3-高级
     */
    private Integer difficulty;
    
    /**
     * 排序方式：price-价格，hot-热门，new-新品，recommend-推荐
     */
    private String sortBy;
    
    /**
     * 页码
     */
    private Integer page = 1;
    
    /**
     * 每页数量
     */
    private Integer size = 20;
}
