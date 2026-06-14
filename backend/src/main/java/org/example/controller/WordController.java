package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.context.UserContextHolder;
import org.example.dto.DeleteInfo;
import org.example.entity.PublicWord;
import org.example.entity.UserWord;
import org.example.mapper.PublicWordMapper;
import org.example.mapper.UserWordMapper;
import org.example.service.AiWordService;
import org.example.service.UserWordService;
import org.example.constant.RedisKeys;
import org.example.utils.RedisUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 单词控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/word")
public class WordController {

    private final UserWordService userWordService;
    private final AiWordService aiWordService;
    private final PublicWordMapper publicWordMapper;
    private final UserWordMapper userWordMapper;
    private final RedisUtil redisUtil;

    @GetMapping("/{id}")
    public Result<UserWord> getWordDetail(@PathVariable Long id) {
        UserWord word = userWordService.getById(id);
        if (word == null) {
            return Result.error("单词不存在");
        }
        return Result.success(word);
    }

    @PostMapping
    public Result addWord(@RequestBody UserWord userWord) {
        userWordService.save(userWord);
        return Result.success("添加成功");
    }

    @DeleteMapping("/remove/{id}")
    public Result deleteWord(@RequestBody DeleteInfo deleteInfo) {
        userWordService.deleteById(deleteInfo.getWordId());
        // 删除单词后清除对应缓存 key
        String user = RedisKeys.userWordList(UserContextHolder.getUserId(), deleteInfo.getBookId());
        redisUtil.delete(user);
        return Result.success("删除成功");
    }

    @GetMapping("/search")
    public Result<List<PublicWord>> searchWord(@RequestParam String keyword) {
        log.debug("🔍 [搜索] 关键词: {}", keyword);
        List<PublicWord> words = publicWordMapper.selectByKeyword(keyword);
        return Result.success(words);
    }

    @GetMapping("/my/search")
    public Result<List<UserWord>> searchMyWord(@RequestParam String keyword) {
        Long userId = UserContextHolder.getUserId();
        log.debug("🔍 [个人单词搜索] 用户: {} | 关键词: {}", userId, keyword);
        List<UserWord> words = userWordMapper.selectByUserIdAndKeyword(userId, keyword);
        return Result.success(words);
    }

    @PostMapping("/ai-fill")
    public Result<UserWord> aiFillWord(@RequestBody Map<String, String> body) {
        String wordText = body.get("wordText");
        log.debug("🤖 [AI填充] 开始处理单词: {}", wordText);
        UserWord word = aiWordService.enrichAndSaveUserWord(wordText);
        log.debug("✅ [AI填充] 完成 | 单词: {}", word.getWordText());
        return Result.success(word);
    }

    @PutMapping("/{id}")
    public Result updateWord(@PathVariable Long id, @RequestBody UserWord userWord) {
        userWord.setId(id);
        userWordService.update(userWord);
        return Result.success("更新成功");
    }
}
