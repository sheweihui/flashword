package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.SeckillMessageLog;

import java.util.List;

/**
 * 秒杀消息日志Mapper接口
 */
@Mapper
public interface SeckillMessageLogMapper {

    int insert(SeckillMessageLog messageLog);

    SeckillMessageLog selectByMessageId(@Param("messageId") String messageId);

    /** 查询指定状态、未超过重试上限的消息 */
    List<SeckillMessageLog> selectByStatus(@Param("status") Integer status, @Param("maxRetry") Integer maxRetry);

    int updateStatus(@Param("messageId") String messageId, @Param("status") Integer status);

    int incrementRetryCount(@Param("messageId") String messageId);

    int setErrorMessage(@Param("messageId") String messageId, @Param("errorMessage") String errorMessage);
}
