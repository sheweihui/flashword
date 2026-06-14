package org.example.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.AiConfig;
import org.example.dto.AiWordInfo;
import org.example.entity.UserWord;
import org.example.mapper.UserWordMapper;
import org.example.service.AiWordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI单词服务实现类 — 通过 Python Agent 获取单词信息
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiWordServiceImpl implements AiWordService {

    private final AiConfig aiConfig;
    private final UserWordMapper userWordMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agent.base-url:http://localhost:8000}")
    private String agentBaseUrl;
    
    /**
     * 通过 Python Agent 获取单词信息
     */
    private AiWordInfo callAiApi(String wordText) {
        if (!aiConfig.isEnabled()) {
            throw new RuntimeException("AI功能未启用，请在配置文件中设置 ai.enabled=true");
        }

        try {
            // 请求 Python Agent
            String url = agentBaseUrl + "/agent/word/enrich";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("word_text", wordText);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);

            Map<String, Object> agentResp = response.getBody();
            String content = agentResp != null ? (String) agentResp.get("content") : null;

            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Agent 返回为空");
            }

            log.debug("🤖 [Agent] 单词补全响应: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
            return parseAiResponse(content);

        } catch (Exception e) {
            log.error("调用 Agent 获取单词信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取单词信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析AI响应
     */
    private AiWordInfo parseAiResponse(String content) {
        try {
            log.debug("🔍 [解析AI响应] 开始解析");
            
            // 尝试提取JSON（可能包含在markdown代码块中）
            String jsonStr = content;
            if (content.contains("```json")) {
                jsonStr = content.substring(content.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (content.contains("```")) {
                jsonStr = content.substring(content.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            jsonStr = jsonStr.trim();
            
            log.debug("解析JSON: {}", jsonStr);
            JsonNode node = objectMapper.readTree(jsonStr);
            
            AiWordInfo wordInfo = new AiWordInfo();
            
            // 提取字段，支持多种字段名格式
            String wordText = extractField(node, "wordText", "word", "text");
            String phonetic = extractField(node, "phonetic", "pronunciation", "ipa");
            String partOfSpeech = extractField(node, "partOfSpeech", "pos", "part_of_speech");
            String definition = extractField(node, "definition", "meaning", "def", "chinese");
            String exampleSentence = extractField(node, "exampleSentence", "example", "sentence", "example_sentence");
            String exampleTranslation = extractField(node, "exampleTranslation", "translation", "example_translation", "example_trans");
            
            wordInfo.setWordText(wordText != null ? wordText.trim() : "");
            wordInfo.setPhonetic(phonetic != null ? phonetic.trim() : "");
            wordInfo.setPartOfSpeech(partOfSpeech != null ? partOfSpeech.trim() : "");
            wordInfo.setDefinition(definition != null ? definition.trim() : "");
            wordInfo.setExampleSentence(exampleSentence != null ? exampleSentence.trim() : "");
            wordInfo.setExampleTranslation(exampleTranslation != null ? exampleTranslation.trim() : "");
            
            log.debug("✅ [解析结果] 单词: {} | 释义: {}", wordInfo.getWordText(), 
                    wordInfo.getDefinition().length() > 50 ? wordInfo.getDefinition().substring(0, 50) + "..." : wordInfo.getDefinition());
            
            return wordInfo;
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析AI响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 从JSON节点中提取字段值，支持多个候选字段名
     */
    private String extractField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                String value = node.get(fieldName).asText();
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }
    
    @Override
    public AiWordInfo getWordInfoByAi(String wordText) {
        log.debug("🔎 [AI查询] 单词: {}", wordText);
        return callAiApi(wordText);
    }
    
    @Override
    public List<AiWordInfo> batchGetWordInfo(List<String> wordList) {
        log.info("📦 [批量AI查询] 共{}个单词", wordList.size());
        
        List<AiWordInfo> results = new ArrayList<>();
        for (String wordText : wordList) {
            try {
                AiWordInfo wordInfo = callAiApi(wordText);
                results.add(wordInfo);
                
                // 避免频繁调用API，添加延迟
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("处理单词 {} 失败: {}", wordText, e.getMessage());
                // 继续处理下一个单词
            }
        }
        
        return results;
    }
    
    @Override
    @Transactional
    public UserWord enrichAndSaveUserWord(String wordText) {
        log.debug("✨ [填充单词] 开始处理: {}", wordText);
        
        // 验证输入参数
        if (wordText == null || wordText.trim().isEmpty()) {
            throw new RuntimeException("单词文本不能为空");
        }
        wordText = wordText.trim();

        // 调用 AI 获取单词信息
        AiWordInfo wordInfo = callAiApi(wordText);
        
        log.debug("📊 [AI数据] 单词: {} | 音标: {} | 词性: {} | 释义长度: {}",
                wordInfo.getWordText(), wordInfo.getPhonetic(), wordInfo.getPartOfSpeech(),
                wordInfo.getDefinition() != null ? wordInfo.getDefinition().length() : 0);

        // 验证 AI 返回的数据
        if (wordInfo.getWordText() == null || wordInfo.getWordText().trim().isEmpty()) {
            // 如果AI没有返回wordText，使用输入的单词
            log.warn("⚠️  [AI警告] 未返回wordText，使用输入值: {}", wordText);
            wordInfo.setWordText(wordText);
        }

        if (wordInfo.getDefinition() == null || wordInfo.getDefinition().trim().isEmpty()) {
            log.error("❌ [AI错误] 未能获取单词释义 | 完整响应: {}", wordInfo);
            throw new RuntimeException("AI未能获取单词释义，请重试。可能是Agent或DeepSeek服务异常。");
        }
        
        // 构建UserWord实体
        UserWord userWord = new UserWord();
        userWord.setWordText(wordInfo.getWordText().trim());
        userWord.setPhonetic(wordInfo.getPhonetic());
        userWord.setPartOfSpeech(wordInfo.getPartOfSpeech());
        userWord.setDefinition(wordInfo.getDefinition());
        userWord.setExampleSentence(wordInfo.getExampleSentence());
        userWord.setExampleTranslation(wordInfo.getExampleTranslation());
        userWord.setCreatedAt(LocalDateTime.now());
        userWord.setUpdatedAt(LocalDateTime.now());
        return userWord;
    }
}
