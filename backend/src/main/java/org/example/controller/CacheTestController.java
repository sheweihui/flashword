package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.constant.RedisKeys;
import org.example.utils.RedisUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 缓存测试控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cache")
public class CacheTestController {

    private final RedisUtil redisUtil;
    
    /**
     * 测试获取用户缓存数据
     */
    @GetMapping("/user/{userId}")
    public Result testUserCache(@PathVariable Long userId) {
        log.info("🔍 [缓存测试] 查询用户缓存数据 | 用户ID: {}", userId);

        // 获取单词本列表缓存
        String vocabBooksKey = RedisKeys.cacheVocabBooks(userId);
        Object vocabBooks = redisUtil.get(vocabBooksKey);

        // 获取所有单词本中的单词缓存
        // 这里需要根据实际的单词本ID来获取，简化处理

        Map<String, Object> cacheData = Map.of(
            "vocabBooksKey", vocabBooksKey,
            "vocabBooks", vocabBooks != null ? vocabBooks : "未找到缓存"
        );

        log.info("✅ [缓存测试] 查询成功 | 用户ID: {}", userId);
        return Result.success(cacheData);
    }

    /**
     * 清除用户缓存数据
     */
    @DeleteMapping("/user/{userId}")
    public Result clearUserCache(@PathVariable Long userId) {
        log.info("🗑️  [缓存测试] 清除用户缓存数据 | 用户ID: {}", userId);

        // 清除单词本列表缓存
        redisUtil.delete(RedisKeys.cacheVocabBooks(userId));

        log.info("✅ [缓存测试] 清除成功 | 用户ID: {}", userId);
        return Result.success("缓存清除成功");
    }
}
