package org.example.service;

import org.example.entity.PublicBookWord;
import org.example.entity.UserBookWord;

import java.util.List;

/**
 * 用户单词书-单词关联服务接口
 */
public interface UserBookWordService {
    
    /**
     * 添加单词到单词书
     */
    void addWordToBook(Long userId, Long bookId, Long wordId);
    
    /**
     * 从单词书移除单词
     */
    void removeWordFromBook(Long userId, Long bookId, Long wordId);
    
    /**
     * 查询单词书中的单词列表
     */
    List<UserBookWord> getWordsByBook(Long userId, Long bookId);
    
    /**
     * 标记单词为已掌握
     */
    void markAsMastered(Long userId, Long bookId, Long wordId);
    
    /**
     * 添加笔记
     */
    void addNote(Long userId, Long bookId, Long wordId, String note);
    
    /**
     * 更新复习次数
     */
    void updateReviewCount(Long userId, Long bookId, Long wordId);
    
    /**
     * 查询未掌握的单词
     */
    List<UserBookWord> getUnmasteredWords(Long userId, Long bookId);
    
    /**
     * 查询已掌握的单词
     */
    List<UserBookWord> getMasteredWords(Long userId, Long bookId);
    
    /**
     * 批量添加单词到单词书
     * @param userId 用户ID
     * @param bookId 单词书ID
     * @param publicBookWords 公共单词书单词列表
     * @return 添加的单词数量
     */
    int batchAddWordsToBook(Long userId, Long bookId, List<PublicBookWord> publicBookWords);
}
