package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.entity.UserBookWord;
import org.example.service.UserBookWordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 单词书-单词关联控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/book-word")
public class BookWordController {

    private final UserBookWordService userBookWordService;

    @DeleteMapping("/remove")
    public Result removeWordFromBook(@RequestParam Long userId,
                                     @RequestParam Long bookId,
                                     @RequestParam Long wordId) {
        userBookWordService.removeWordFromBook(userId, bookId, wordId);
        return Result.success("移除成功");
    }

    @GetMapping("/list")
    public Result getWordsByBook(@RequestParam Long userId,
                                 @RequestParam Long bookId) {
        List<UserBookWord> words = userBookWordService.getWordsByBook(userId, bookId);
        return Result.success(words);
    }

    @PutMapping("/master")
    public Result markAsMastered(@RequestParam Long userId,
                                 @RequestParam Long bookId,
                                 @RequestParam Long wordId) {
        userBookWordService.markAsMastered(userId, bookId, wordId);
        return Result.success("标记成功");
    }

    @PutMapping("/note")
    public Result addNote(@RequestParam Long userId,
                          @RequestParam Long bookId,
                          @RequestParam Long wordId,
                          @RequestParam String note) {
        userBookWordService.addNote(userId, bookId, wordId, note);
        return Result.success("添加笔记成功");
    }

    @PutMapping("/review")
    public Result updateReviewCount(@RequestParam Long userId,
                                    @RequestParam Long bookId,
                                    @RequestParam Long wordId) {
        userBookWordService.updateReviewCount(userId, bookId, wordId);
        return Result.success("更新成功");
    }

    @GetMapping("/unmastered")
    public Result getUnmasteredWords(@RequestParam Long userId,
                                     @RequestParam Long bookId) {
        List<UserBookWord> words = userBookWordService.getUnmasteredWords(userId, bookId);
        return Result.success(words);
    }

    @GetMapping("/mastered")
    public Result getMasteredWords(@RequestParam Long userId,
                                   @RequestParam Long bookId) {
        List<UserBookWord> words = userBookWordService.getMasteredWords(userId, bookId);
        return Result.success(words);
    }
}
