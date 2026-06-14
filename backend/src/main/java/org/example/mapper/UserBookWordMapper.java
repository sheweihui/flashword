package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.UserBookWord;

import java.util.List;

/**
 * 用户单词书-单词关联Mapper接口
 */
@Mapper
public interface UserBookWordMapper {
    
    /**
     * 插入关联记录
     */
    int insert(UserBookWord userBookWord);
    
    /**
     * 根据ID查询
     */
    UserBookWord selectById(@Param("id") Long id);
    
    /**
     * 根据单词书ID查询单词列表
     */
    List<UserBookWord> selectByBookId(@Param("bookId") Long bookId);
    
    /**
     * 根据用户ID和单词书ID查询
     */
    List<UserBookWord> selectByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);
    
    /**
     * 更新掌握状态
     */
    int updateMastered(@Param("id") Long id, @Param("mastered") Integer mastered);
    
    /**
     * 更新复习次数
     */
    int updateReviewCount(@Param("id") Long id, @Param("reviewCount") Integer reviewCount);
    
    /**
     * 删除关联记录
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<UserBookWord> list);
    
    /**
     * 统计单词书的单词数量
     */
    int countByBookId(@Param("bookId") Long bookId);

    /**
     * 统计用户所有单词总数
     */
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计用户已掌握的单词数
     */
    int countMasteredByUserId(@Param("userId") Long userId);

    /**
     * 统计用户今日复习的单词数
     */
    int countReviewedTodayByUserId(@Param("userId") Long userId);

    /**
     * 统计用户今日新学单词数
     */
    int countNewTodayByUserId(@Param("userId") Long userId);
}
