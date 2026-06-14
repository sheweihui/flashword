package org.example.service;

import org.example.dto.AiWordInfo;
import org.example.entity.UserWord;

import java.util.List;

/**
 * AI单词服务接口
 */
public interface AiWordService {
    
    /**
     * 通过AI获取单词详细信息
     * @param wordText 单词文本
     * @return 单词详细信息
     */
    AiWordInfo getWordInfoByAi(String wordText);
    
    /**
     * 批量获取单词信息
     * @param wordList 单词列表
     * @return 单词详细信息列表
     */
    List<AiWordInfo> batchGetWordInfo(List<String> wordList);
    
    /**
     * 使用AI填充单词并保存到数据库
     * @param wordText 单词文本
     * @return 保存后的单词实体
     */
    UserWord enrichAndSaveUserWord(String wordText);
}
