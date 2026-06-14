package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserVocabularyBook;

import java.util.List;

/**
 * 用户单词书Mapper接口
 */
@Mapper
public interface UserVocabularyBookMapper {
    
    /**
     * 插入用户单词书
     */
    int insert(UserVocabularyBook book);
    
    /**
     * 根据ID查询用户单词书
     */
    UserVocabularyBook selectById(@Param("id") Long id);
    
    /**
     * 根据用户ID查询单词书列表
     */
    List<UserVocabularyBook> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 更新用户单词书
     */
    int update(UserVocabularyBook book);
    
    /**
     * 删除用户单词书
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 更新单词数量
     */
    int updateWordCount(@Param("id") Long id, @Param("wordCount") Integer wordCount);
}
