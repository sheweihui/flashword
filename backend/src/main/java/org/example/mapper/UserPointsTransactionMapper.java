package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserPointsTransaction;

import java.util.List;

/**
 * 用户积分交易记录Mapper接口
 */
@Mapper
public interface UserPointsTransactionMapper {
    
    /**
     * 插入交易记录
     */
    int insert(UserPointsTransaction transaction);
    
    /**
     * 根据用户ID查询交易记录
     */
    List<UserPointsTransaction> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID分页查询
     */
    List<UserPointsTransaction> selectByUserIdWithPage(@Param("userId") Long userId, 
                                                        @Param("offset") int offset, 
                                                        @Param("limit") int limit);
}
