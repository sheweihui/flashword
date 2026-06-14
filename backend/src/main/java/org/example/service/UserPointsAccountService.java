package org.example.service;

import org.example.entity.UserPointsAccount;

/**
 * 用户积分账户服务接口
 */
public interface UserPointsAccountService {
    
    /**
     * 创建用户积分账户（注册时调用）
     * @param userId 用户ID
     * @return 积分账户
     */
    UserPointsAccount createAccount(Long userId);
    
    /**
     * 获取用户积分账户
     * @param userId 用户ID
     * @return 积分账户
     */
    UserPointsAccount getAccountByUserId(Long userId);
    
    /**
     * 增加积分
     * @param userId 用户ID
     * @param amount 增加的积分数
     * @param type 交易类型
     * @param description 描述
     * @param referenceId 关联ID
     * @return 增加后的余额
     */
    Integer addPoints(Long userId, Integer amount, Integer type, String description, Long referenceId);
    
    /**
     * 减少积分（消费）
     * @param userId 用户ID
     * @param amount 减少的积分数
     * @param type 交易类型
     * @param description 描述
     * @param referenceId 关联ID
     * @return 减少后的余额
     */
    Integer deductPoints(Long userId, Integer amount, Integer type, String description, Long referenceId);
    
    /**
     * 减少积分（支持幂等性标识）
     */
    Integer deductPoints(Long userId, Integer amount, Integer type, String description, Long referenceId, String idempotencyKey);
}
