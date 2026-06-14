package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.StorePurchaseRecord;

import java.util.List;

/**
 * 商店购买记录Mapper接口
 */
@Mapper
public interface StorePurchaseRecordMapper {
    
    /**
     * 插入购买记录
     */
    int insert(StorePurchaseRecord record);
    
    /**
     * 根据ID查询
     */
    StorePurchaseRecord selectById(@Param("id") Long id);
    
    /**
     * 根据用户ID查询购买记录
     */
    List<StorePurchaseRecord> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 检查用户是否已购买某商品
     */
    StorePurchaseRecord selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
}
