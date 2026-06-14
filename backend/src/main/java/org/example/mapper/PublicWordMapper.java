package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.PublicWord;

import java.util.List;

/**
 * 公共单词Mapper接口
 */
@Mapper
public interface PublicWordMapper {
    
    /**
     * 插入公共单词
     */
    int insert(PublicWord publicWord);
    
    /**
     * 根据ID查询
     */
    PublicWord selectById(@Param("id") Long id);
    
    /**
     * 根据单词文本查询
     */
    PublicWord selectByWordText(@Param("wordText") String wordText);
    
    /**
     * 根据ID列表批量查询
     */
    List<PublicWord> selectByIds(@Param("ids") List<Long> ids);
    
    /**
     * 根据标签查询
     */
    List<PublicWord> selectByTags(@Param("tags") String tags);

    /**
     * 根据关键词模糊搜索（前 10 条）
     */
    List<PublicWord> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<PublicWord> list);
}
