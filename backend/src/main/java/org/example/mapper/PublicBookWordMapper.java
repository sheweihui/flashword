package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.PublicBookWord;

import java.util.List;

/**
 * 公共单词书-单词关联Mapper接口
 */
@Mapper
public interface PublicBookWordMapper {
    
    /**
     * 插入关联记录
     */
    int insert(PublicBookWord publicBookWord);
    
    /**
     * 根据单词书ID查询单词列表
     */
    List<PublicBookWord> selectByBookId(@Param("bookId") Long bookId);
    
    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<PublicBookWord> list);
    
    /**
     * 统计单词书的单词数量
     */
    int countByBookId(@Param("bookId") Long bookId);
}
