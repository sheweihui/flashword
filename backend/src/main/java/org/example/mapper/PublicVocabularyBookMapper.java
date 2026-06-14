package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.PublicVocabularyBook;

import java.util.List;

/**
 * 公共单词书Mapper接口
 */
@Mapper
public interface PublicVocabularyBookMapper {
    
    /**
     * 插入公共单词书
     */
    int insert(PublicVocabularyBook book);
    
    /**
     * 根据ID查询
     */
    PublicVocabularyBook selectById(@Param("id") Long id);
    
    /**
     * 查询所有上架的单词书
     */
    List<PublicVocabularyBook> selectAll();
    
    /**
     * 根据分类查询
     */
    List<PublicVocabularyBook> selectByCategory(@Param("category") String category);
    
    /**
     * 根据难度查询
     */
    List<PublicVocabularyBook> selectByDifficulty(@Param("difficulty") Integer difficulty);
    
    /**
     * 更新单词数量
     */
    int updateWordCount(@Param("id") Long id, @Param("wordCount") Integer wordCount);
}
