package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.UserCheckin;
import org.example.mapper.UserCheckinMapper;
import org.example.service.UserCheckinService;
import org.example.service.UserPointsAccountService;
import org.example.vo.CheckinVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 用户签到服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserCheckinServiceImpl implements UserCheckinService {

    private final UserCheckinMapper userCheckinMapper;
    private final UserPointsAccountService userPointsAccountService;
    
    @Override
    @Transactional
    public CheckinVO checkin(Long userId) {
        CheckinVO vo = new CheckinVO();
        LocalDate today = LocalDate.now();
        
        // 检查今天是否已签到
        UserCheckin existingCheckin = userCheckinMapper.selectByUserIdAndDate(userId, today);
        if (existingCheckin != null) {
            vo.setCheckedIn(false);
            vo.setMessage("今日已签到");
            vo.setContinuousDays(existingCheckin.getContinuousDays());
            vo.setPointsEarned(0);
            return vo;
        }
        // 获取连续签到天数
        Integer continuousDays = userCheckinMapper.getContinuousDays(userId);
        if (continuousDays == null) {
            continuousDays = 0;
        }
        
        // 计算新的连续天数
        int newContinuousDays = continuousDays + 1;
        int pointsEarned = 10; // 基础奖励10积分
        
        // 连续签到奖励
        if (newContinuousDays >= 7) {
            pointsEarned += 20; // 连续7天额外奖励20积分
        } else if (newContinuousDays >= 3) {
            pointsEarned += 10; // 连续3天额外奖励10积分
        }
        
        // 创建签到记录
        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setContinuousDays(newContinuousDays);
        checkin.setPointsEarned(pointsEarned);
        
        userCheckinMapper.insert(checkin);
        
        // 增加积分
        userPointsAccountService.addPoints(
            userId, 
            pointsEarned, 
            2, // 每日签到
            "每日签到奖励", 
            null
        );
        
        vo.setCheckedIn(true);
        vo.setMessage("签到成功");
        vo.setContinuousDays(newContinuousDays);
        vo.setPointsEarned(pointsEarned);
        
        log.info("✅ [签到] 用户ID: {} | 连续天数: {} | 获得积分: {}", userId, newContinuousDays, pointsEarned);
        return vo;
    }
    
    @Override
    public Integer getContinuousDays(Long userId) {
        return userCheckinMapper.getContinuousDays(userId);
    }
}
