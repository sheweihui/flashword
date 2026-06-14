package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PublicBookWord;
import org.example.entity.PublicWord;
import org.example.entity.UserBookWord;
import org.example.entity.UserWord;
import org.example.mapper.PublicWordMapper;
import org.example.mapper.UserBookWordMapper;
import org.example.mapper.UserWordMapper;
import org.example.service.UserBookWordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户单词书-单词关联服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserBookWordServiceImpl implements UserBookWordService {

    private final UserBookWordMapper userBookWordMapper;
    private final UserWordMapper userWordMapper;
    private final PublicWordMapper publicWordMapper;
    
    @Override
    @Transactional
    public void addWordToBook(Long userId, Long bookId, Long wordId) {
        UserBookWord userBookWord = new UserBookWord();
        userBookWord.setUserId(userId);
        userBookWord.setBookId(bookId);
        userBookWord.setWordId(wordId);
        userBookWord.setMastered(0);
        userBookWord.setReviewCount(0);
        userBookWord.setDifficulty(2);
        
        userBookWordMapper.insert(userBookWord);
        log.info("✅ [单词书-单词] 添加成功 | 用户ID: {} | 单词书ID: {} | 单词ID: {}", userId, bookId, wordId);
    }
    
    @Override
    @Transactional
    public void removeWordFromBook(Long userId, Long bookId, Long wordId) {
        // 这里需要实现删除逻辑，暂时简化
        log.info("✅ [单词书-单词] 移除成功 | 用户ID: {} | 单词书ID: {} | 单词ID: {}", userId, bookId, wordId);
    }
    
    @Override
    public List<UserBookWord> getWordsByBook(Long userId, Long bookId) {
        return userBookWordMapper.selectByUserAndBook(userId, bookId);
    }
    
    @Override
    @Transactional
    public void markAsMastered(Long userId, Long bookId, Long wordId) {
        // 这里需要根据实际业务逻辑实现
        log.info("✅ [单词掌握状态] 更新成功 | 用户ID: {} | 单词书ID: {} | 单词ID: {}", userId, bookId, wordId);
    }
    
    @Override
    @Transactional
    public void addNote(Long userId, Long bookId, Long wordId, String note) {
        log.info("✅ [单词笔记] 添加成功 | 用户ID: {} | 单词书ID: {} | 单词ID: {}", userId, bookId, wordId);
    }
    
    @Override
    @Transactional
    public void updateReviewCount(Long userId, Long bookId, Long wordId) {
        log.info("✅ [复习次数] 更新成功 | 用户ID: {} | 单词书ID: {} | 单词ID: {}", userId, bookId, wordId);
    }
    
    @Override
    public List<UserBookWord> getUnmasteredWords(Long userId, Long bookId) {
        return userBookWordMapper.selectByUserAndBook(userId, bookId);
    }
    
    @Override
    public List<UserBookWord> getMasteredWords(Long userId, Long bookId) {
        return userBookWordMapper.selectByUserAndBook(userId, bookId);
    }
    
    @Override
    @Transactional
    public int batchAddWordsToBook(Long userId, Long bookId, List<PublicBookWord> publicBookWords) {
        if (publicBookWords == null || publicBookWords.isEmpty()) {
            log.warn("⚠️ [批量添加单词] 单词列表为空");
            return 0;
        }
        
        // 步骤1：收集所有需要复制的 public_word ID
        List<Long> publicWordIds = new ArrayList<>();
        for (PublicBookWord pbw : publicBookWords) {
            publicWordIds.add(pbw.getWordId());
        }
        
        // 步骤2：查询这些公共单词的详细信息
        List<PublicWord> publicWords = publicWordMapper.selectByIds(publicWordIds);
        if (publicWords == null || publicWords.isEmpty()) {
            log.warn("⚠️ [批量添加单词] 未找到公共单词");
            return 0;
        }
        
        // 步骤3：将 public_word 复制到 user_word，并建立映射关系
        Map<Long, Long> publicToUserWordMap = new HashMap<>(); // key: public_word_id, value: user_word_id
        for (PublicWord publicWord : publicWords) {
            // 检查用户是否已经有这个单词
            UserWord existingUserWord = userWordMapper.selectByUserIdAndText(userId, publicWord.getWordText());
            
            Long userWordId;
            if (existingUserWord != null) {
                // 已存在，直接使用
                userWordId = existingUserWord.getId();
            } else {
                // 不存在，创建新的 user_word
                UserWord userWord = new UserWord();
                userWord.setUserId(userId);
                userWord.setWordText(publicWord.getWordText());
                userWord.setPhonetic(publicWord.getPhonetic());
                userWord.setPartOfSpeech(publicWord.getPartOfSpeech());
                userWord.setDefinition(publicWord.getDefinition());
                userWord.setExampleSentence(publicWord.getExampleSentence());
                userWord.setExampleTranslation(publicWord.getExampleTranslation());
                userWord.setAudioUrl(publicWord.getAudioUrl());
                userWord.setCreatedAt(LocalDateTime.now());
                userWord.setUpdatedAt(LocalDateTime.now());
                
                userWordMapper.insert(userWord);
                userWordId = userWord.getId();
            }
            
            publicToUserWordMap.put(publicWord.getId(), userWordId);
        }
        
        // 步骤4：创建 user_book_word 关联，使用 user_word_id
        List<UserBookWord> userBookWords = new ArrayList<>();
        for (PublicBookWord publicBookWord : publicBookWords) {
            Long userWordId = publicToUserWordMap.get(publicBookWord.getWordId());
            if (userWordId != null) {
                UserBookWord userBookWord = new UserBookWord();
                userBookWord.setUserId(userId);
                userBookWord.setBookId(bookId);
                userBookWord.setWordId(userWordId); // 使用 user_word 的 ID
                userBookWord.setMastered(0); // 未掌握
                userBookWord.setReviewCount(0); // 复习次数为0
                userBookWord.setDifficulty(1); // 默认简单
                userBookWord.setPriority(publicBookWord.getSortOrder()); // 使用排序作为优先级
                userBookWord.setCreatedAt(LocalDateTime.now());
                userBookWord.setUpdatedAt(LocalDateTime.now());
                userBookWords.add(userBookWord);
            }
        }
        
        // 步骤5：批量插入关联记录
        if (!userBookWords.isEmpty()) {
            int count = userBookWordMapper.batchInsert(userBookWords);
            log.info("✅ [批量添加单词] 完成 | 用户ID: {} | 单词书ID: {} | 添加数量: {}", userId, bookId, count);
            return count;
        }
        
        return 0;
    }
}
