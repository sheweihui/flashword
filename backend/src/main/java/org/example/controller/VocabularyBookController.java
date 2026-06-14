package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.context.UserContextHolder;
import org.example.dto.CreateVocabularyBook;
import org.example.dto.DeleteInfo;
import org.example.dto.PutInfo;
import org.example.dto.WordDTO;
import org.example.entity.UserVocabularyBook;
import org.example.entity.UserWord;
import org.example.service.AiWordService;
import org.example.service.UserVocabularyBookService;
import org.example.service.UserWordService;
import org.example.constant.RedisKeys;
import org.example.mapper.UserBookWordMapper;
import org.example.service.UserCheckinService;
import org.example.vo.StudyStatsVO;
import org.example.utils.RedisUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 单词书控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/vocabulary-book")
public class VocabularyBookController {

    private final UserVocabularyBookService userVocabularyBookService;
    private final UserWordService userWordService;
    private final AiWordService aiWordService;
    private final UserCheckinService userCheckinService;
    private final UserBookWordMapper userBookWordMapper;
    private final RedisUtil redisUtil;

    @PostMapping
    public Result createVocabularyBook(@RequestBody CreateVocabularyBook createVocabularyBook) {
        createVocabularyBook.setCoverImage("");
        userVocabularyBookService.createVocabularyBook(
                UserContextHolder.getUserId(),
                createVocabularyBook.getBookName(),
                createVocabularyBook.getDescription(),
                createVocabularyBook.getCoverImage()
        );
        return Result.success("创建成功");
    }

    @GetMapping({"/list/{userId}", "/vocab/list/{userId}"})
    public Result<List<UserVocabularyBook>> getBooksByUserId(@PathVariable Long userId) {
        userId = UserContextHolder.getUserId();
        log.debug("💾 [DB] 从数据库查询用户单词本列表 | 用户ID: {}", userId);
        List<UserVocabularyBook> books = userVocabularyBookService.getBooksByUserId(userId);
        log.debug("✅ [DB] 获取单词本列表成功 | 用户ID: {} | 数量: {}", userId, books != null ? books.size() : 0);
        return Result.success(books);
    }

    @GetMapping("/study/stats")
    public Result<StudyStatsVO> getStudyStats() {
        Long userId = UserContextHolder.getUserId();
        log.debug("📊 [学习统计] 用户ID: {}", userId);

        int totalWords = userBookWordMapper.countByUserId(userId);
        int masteredWords = userBookWordMapper.countMasteredByUserId(userId);
        int todayLearned = userBookWordMapper.countNewTodayByUserId(userId);
        int todayReviewed = userBookWordMapper.countReviewedTodayByUserId(userId);
        Integer rawStreak = userCheckinService.getContinuousDays(userId);
        int streakDays = rawStreak != null ? rawStreak : 0;

        StudyStatsVO vo = new StudyStatsVO();
        vo.setTotalWords(totalWords);
        vo.setMasteredWords(masteredWords);
        vo.setMasteryRate(totalWords > 0
                ? String.format("%.1f%%", masteredWords * 100.0 / totalWords)
                : "0.0%");
        vo.setTodayLearned(todayLearned);
        vo.setTodayReviewed(todayReviewed);
        vo.setStreakDays(streakDays);

        return Result.success(vo);
    }

    @GetMapping("/words")
    public Result<List<UserWord>> getBookByIdAllWord(@RequestParam("bookId") Long bookId) {
        if (bookId == null) {
            return Result.error("单词书ID不能为空");
        }

        String redisKey = RedisKeys.userWordList(UserContextHolder.getUserId(), bookId);
        log.debug("🔍 [Redis] 尝试从 Redis 获取单词数据 | Key: {}", redisKey);
        @SuppressWarnings("unchecked")
        List<UserWord> words = (List<UserWord>) redisUtil.get(redisKey);
        if (words != null) {
            redisUtil.expire(redisKey, 1, TimeUnit.DAYS);
            log.info("✅ [Redis] 缓存命中 | 单词数: {} | Key: {}", words.size(), redisKey);
            return Result.success(words);
        }

        log.info("💾 [DB] Redis 缓存未命中，从数据库查询 | 单词书ID: {}", bookId);
        List<UserWord> words1 = userVocabularyBookService.getBookByIdGetALLWORD(bookId);
        redisUtil.set(redisKey, words1, 1, TimeUnit.DAYS);
        log.info("✅ [DB→Redis] 从数据库获取并缓存到 Redis | 单词书ID: {} | 单词数: {} | Key: {}",
                bookId, words1 != null ? words1.size() : 0, redisKey);
        return Result.success(words1);
    }

    @PutMapping("/{id}")
    public Result updateVocabularyBook(@RequestBody PutInfo putInfo, @PathVariable Long id) {
        String bookName = putInfo.getBookName();
        String description = putInfo.getDescription();
        String coverImage = "";
        Integer isPublic = 0;
        userVocabularyBookService.updateVocabularyBook(id, bookName, description, coverImage, isPublic);
        Long userId = UserContextHolder.getUserId();
        redisUtil.delete(RedisKeys.userBooks(userId));
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result deleteVocabularyBook(@PathVariable Long id) {
        userVocabularyBookService.deleteVocabularyBook(id);
        return Result.success("删除成功");
    }

    @PostMapping("/add-word")
    public Result addWordToBook(@RequestBody WordDTO wordDTO) {
        log.debug("➕ [添加单词] 单词书ID: {} | 单词: {}", wordDTO.getBookId(), wordDTO.getWordText());
        userVocabularyBookService.addWordToBook(wordDTO);
        String redisKey = RedisKeys.userWordList(UserContextHolder.getUserId(), wordDTO.getBookId());
        redisUtil.delete(redisKey);
        log.debug("🗑️ [清除缓存] Key: {}", redisKey);
        return Result.success("添加成功");
    }

    @DeleteMapping("/word/remove")
    public Result deleteWord(@RequestBody DeleteInfo deleteInfo) {
        userWordService.deleteById(deleteInfo.getWordId());
        // 删除单词后清除对应缓存 key
        String user = RedisKeys.userWordList(UserContextHolder.getUserId(), deleteInfo.getBookId());
        redisUtil.delete(user);
        return Result.success("删除成功");
    }
}
