package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.context.UserContextHolder;
import org.example.dto.WordDTO;
import org.example.entity.UserBookWord;
import org.example.entity.UserVocabularyBook;
import org.example.entity.UserWord;
import org.example.mapper.UserBookWordMapper;
import org.example.mapper.UserVocabularyBookMapper;
import org.example.mapper.UserWordMapper;
import org.example.service.UserVocabularyBookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户单词书服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserVocabularyBookServiceImpl implements UserVocabularyBookService {

    private final UserVocabularyBookMapper userVocabularyBookMapper;
    private final UserBookWordMapper userBookWordMapper;
    private final UserWordMapper userWordMapper;

    @Override
    @Transactional
    public Long createVocabularyBook(Long userId, String bookName, String description, String coverImage) {
        UserVocabularyBook book = new UserVocabularyBook();
        book.setUserId(userId);
        book.setBookName(bookName);
        book.setDescription(description);
        book.setCoverImage(coverImage);
        book.setIsPublic(0);
        book.setSourceType(1); // 手动创建
        book.setWordCount(0);
        
        userVocabularyBookMapper.insert(book);
        log.info("✅ [用户单词书] 创建成功 | 书名: {} | 用户ID: {} | ID: {}", bookName, userId, book.getId());
        return book.getId();
    }

    @Override
    public List<UserVocabularyBook> getBooksByUserId(Long userId) {
        return userVocabularyBookMapper.selectByUserId(userId);
    }

    @Override
    public UserVocabularyBook getBookById(Long id) {
        return userVocabularyBookMapper.selectById(id);
    }

    @Override
    @Transactional
    public void updateVocabularyBook(Long id, String bookName, String description, String coverImage, Integer isPublic) {
        UserVocabularyBook book = userVocabularyBookMapper.selectById(id);
        if (book != null) {
            book.setBookName(bookName);
            book.setDescription(description);
            book.setCoverImage(coverImage);
            book.setIsPublic(isPublic);
            userVocabularyBookMapper.update(book);
            log.info("✅ [用户单词书] 更新成功 | ID: {}", id);
        }
    }

    @Override
    @Transactional
    public void deleteVocabularyBook(Long id) {
        userVocabularyBookMapper.deleteById(id);
        log.info("✅ [用户单词书] 删除成功 | ID: {}", id);
    }

    @Override
    @Transactional
    public void addWordToBook(WordDTO wordDTO) {
        // 检查单词是否已存在
        wordDTO.setUserId(UserContextHolder.getUserId());
        UserWord existingWord = userWordMapper.selectByUserIdAndText(UserContextHolder.getUserId(), wordDTO.getWordText());
        
        Long wordId;
        if (existingWord == null) {
            // 创建新单词
            UserWord userWord = new UserWord();
            userWord.setUserId(UserContextHolder.getUserId());
            userWord.setWordText(wordDTO.getWordText());
            userWord.setPhonetic(wordDTO.getPhonetic());
            userWord.setPartOfSpeech(wordDTO.getPartOfSpeech());
            userWord.setDefinition(wordDTO.getDefinition());
            userWord.setExampleSentence(wordDTO.getExampleSentence());
            userWord.setExampleTranslation(wordDTO.getExampleTranslation());
            userWord.setAudioUrl(wordDTO.getAudioUrl());
            
            userWordMapper.insert(userWord);
            wordId = userWord.getId();
            log.info("✅ [用户单词] 创建新单词 | 单词: {}", wordDTO.getWordText());
        } else {
            wordId = existingWord.getId();
            log.debug("📝 [用户单词] 使用已有单词 | ID: {}", wordId);
        }
        
        // 添加关联
        UserBookWord userBookWord = new UserBookWord();
        userBookWord.setUserId(wordDTO.getUserId());
        userBookWord.setBookId(wordDTO.getBookId());
        userBookWord.setWordId(wordId);
        userBookWord.setMastered(0);
        userBookWord.setReviewCount(0);
        userBookWord.setDifficulty(2);
        
        userBookWordMapper.insert(userBookWord);
        log.info("✅ [单词书-单词] 添加成功 | 单词书ID: {} | 单词ID: {}", wordDTO.getBookId(), wordId);
    }

    @Override
    public List<UserWord> getBookByIdGetALLWORD(Long bookId) {
        // 查询单词书-单词关联
        log.debug("💾 [DB] 从数据库查询单词书-单词关联 | 单词书ID: {}", bookId);
        List<UserBookWord> bookWords = userBookWordMapper.selectByBookId(bookId);
        
        // 获取单词详情
        List<UserWord> words = new ArrayList<>();
        for (UserBookWord bookWord : bookWords) {
            log.trace("💾 [DB] 从数据库查询单词详情 | 单词ID: {}", bookWord.getWordId());
            UserWord word = userWordMapper.selectById(bookWord.getWordId());
            if (word != null) {
                words.add(word);
            }
        }
        
        log.debug("✅ [DB] 从数据库获取单词书单词列表完成 | 单词书ID: {} | 单词数: {}", bookId, words.size());
        return words;
    }

    @Override
    public List<UserVocabularyBook> listByUserId(Long userId) {
        log.debug("💾 [DB] 从数据库查询用户单词本列表 | 用户ID: {}", userId);
        return userVocabularyBookMapper.selectByUserId(userId);
    }
}
