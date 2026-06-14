package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserWord;

import java.util.List;

/**
 * 用户单词Mapper接口
 */
@Mapper
public interface UserWordMapper {
    
    /**
     * 插入用户单词
     */
    int insert(UserWord userWord);
    
    /**
     * 根据ID查询用户单词
     */
    UserWord selectById(@Param("id") Long id);
    
    /**
     * 根据用户ID查询单词列表
     */
    List<UserWord> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID和单词文本查询
     */
    UserWord selectByUserIdAndText(@Param("userId") Long userId, @Param("wordText") String wordText);

    /**
     * 根据用户ID和关键词模糊搜索单词
     */
    List<UserWord> selectByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);
    
    /**
     * 更新用户单词
     */
    int update(UserWord userWord);
    
    /**
     * 删除用户单词
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 批量插入用户单词
     */
    int batchInsert(@Param("list") List<UserWord> userWords);
}
