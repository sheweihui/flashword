package org.example.service;

import org.example.vo.CheckinVO;

/**
 * 用户签到服务接口
 */
public interface UserCheckinService {
    
    /**
     * 每日签到
     * @param userId 用户ID
     * @return 签到结果
     */
    CheckinVO checkin(Long userId);
    
    /**
     * 获取连续签到天数
     * @param userId 用户ID
     * @return 连续签到天数
     */
    Integer getContinuousDays(Long userId);
}
