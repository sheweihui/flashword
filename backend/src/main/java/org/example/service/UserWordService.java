package org.example.service;

import org.example.entity.UserWord;

import java.util.List;

/**
 * 用户单词服务接口
 */
public interface UserWordService {
    
    /**
     * 根据ID获取单词
     */
    UserWord getById(Long id);
    
    /**
     * 根据用户ID查询单词列表
     */
    List<UserWord> getByUserId(Long userId);
    
    /**
     * 根据用户ID和单词文本查询
     */
    UserWord getByUserIdAndText(Long userId, String wordText);
    
    /**
     * 保存单词
     */
    void save(UserWord userWord);
    
    /**
     * 更新单词
     */
    void update(UserWord userWord);
    
    /**
     * 删除单词
     */
    void deleteById(Long id);
    
    /**
     * 批量插入用户单词
     */
    void batchInsert(List<UserWord> userWords);
}
