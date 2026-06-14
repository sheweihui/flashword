package org.example.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体类
 */
@Data
public class SeckillActivity {
    
    /** 活动ID */
    private Long id;
    /** 关联商品ID */
    private Long productId;
    /** 秒杀价格（积分） */
    private Integer seckillPrice;
    /** 库存数量 */
    private Integer stock;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
}
