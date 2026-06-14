package org.example.service;

import org.example.dto.WordDTO;
import org.example.entity.UserVocabularyBook;
import org.example.entity.UserWord;

import java.util.List;

/**
 * 用户单词书服务接口
 */
public interface UserVocabularyBookService {

    /**
     * 创建单词书
     * @return 创建的单词书ID
     */
    Long createVocabularyBook(Long userId, String bookName, String description, String coverImage);

    /**
     * 查询用户的所有单词书
     */
    List<UserVocabularyBook> getBooksByUserId(Long userId);

    /**
     * 查询单词书详情
     */
    UserVocabularyBook getBookById(Long id);

    /**
     * 更新单词书
     */
    void updateVocabularyBook(Long id, String bookName, String description, String coverImage, Integer isPublic);

    /**
     * 删除单词书
     */
    void deleteVocabularyBook(Long id);

    /**
     * 添加单词到单词书
     */
    void addWordToBook(WordDTO wordDTO);

    /**
     * 查询单词书的所有单词
     */
    List<UserWord> getBookByIdGetALLWORD(Long bookId);

    /**
     * 根据用户ID查询单词书列表
     */
    List<UserVocabularyBook> listByUserId(Long userId);
}
