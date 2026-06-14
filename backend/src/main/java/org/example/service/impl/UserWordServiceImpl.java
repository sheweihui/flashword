package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.UserWord;
import org.example.mapper.UserWordMapper;
import org.example.service.UserWordService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户单词服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserWordServiceImpl implements UserWordService {

    private final UserWordMapper userWordMapper;
    
    @Override
    public UserWord getById(Long id) {
        return userWordMapper.selectById(id);
    }
    
    @Override
    public List<UserWord> getByUserId(Long userId) {
        return userWordMapper.selectByUserId(userId);
    }
    
    @Override
    public UserWord getByUserIdAndText(Long userId, String wordText) {
        return userWordMapper.selectByUserIdAndText(userId, wordText);
    }
    
    @Override
    public void save(UserWord userWord) {
        userWordMapper.insert(userWord);
        log.info("✅ [用户单词] 保存成功 | 单词: {}", userWord.getWordText());
    }
    
    @Override
    public void update(UserWord userWord) {
        userWordMapper.update(userWord);
        log.info("✅ [用户单词] 更新成功 | ID: {}", userWord.getId());
    }
    
    @Override
    public void deleteById(Long id) {
        userWordMapper.deleteById(id);
        log.info("✅ [用户单词] 删除成功 | ID: {}", id);
    }
    
    @Override
    public void batchInsert(List<UserWord> userWords) {
        if (userWords != null && !userWords.isEmpty()) {
            userWordMapper.batchInsert(userWords);
            log.info("✅ [用户单词] 批量插入成功 | 数量: {}", userWords.size());
        }
    }
}
