package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserPointsAccount;

/**
 * 用户积分账户Mapper接口
 */
@Mapper
public interface UserPointsAccountMapper {
    
    /**
     * 插入积分账户
     */
    int insert(UserPointsAccount account);
    
    /**
     * 根据用户ID查询
     */
    UserPointsAccount selectByUserId(@Param("userId") Long userId);
    
    /**
     * 更新积分余额
     */
    int updateBalance(@Param("userId") Long userId, @Param("balance") Integer balance);
    
    /**
     * 增加积分
     */
    int addPoints(@Param("userId") Long userId, @Param("points") Integer points);
    
    /**
     * 扣除积分
     */
    int deductPoints(@Param("userId") Long userId, @Param("points") Integer points);
}
