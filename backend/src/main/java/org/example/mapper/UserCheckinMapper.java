package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserCheckin;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户签到记录Mapper接口
 */
@Mapper
public interface UserCheckinMapper {
    
    /**
     * 插入签到记录
     */
    int insert(UserCheckin checkin);
    
    /**
     * 根据用户ID和日期查询
     */
    UserCheckin selectByUserIdAndDate(@Param("userId") Long userId, @Param("checkinDate") LocalDate checkinDate);
    
    /**
     * 查询用户最近的签到记录
     */
    UserCheckin selectLatestByUserId(@Param("userId") Long userId);
    
    /**
     * 查询用户连续签到天数
     */
    Integer getContinuousDays(@Param("userId") Long userId);
    
    /**
     * 根据用户ID查询签到记录
     */
    List<UserCheckin> selectByUserId(@Param("userId") Long userId);
}
