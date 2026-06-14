package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.context.UserContextHolder;
import org.example.service.PurchaseService;
import org.springframework.web.bind.annotation.*;

/**
 * 购买测试控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/sync/{storeBookId}")
    public Result purchaseBookSync(@PathVariable Long storeBookId) {
        long startTime = System.currentTimeMillis();
        Long userId = UserContextHolder.getUserId();
        log.info("🔄 [同步购买测试] 开始 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
        Long userBookId = purchaseService.purchaseBookSync(userId, storeBookId);
        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ [同步购买测试] 完成 | 用户ID: {} | 商店书ID: {} | 耗时: {}ms",
                userId, storeBookId, duration);
        return Result.success("同步购买成功", userBookId);
    }

    @PostMapping("/async/{storeBookId}")
    public Result purchaseBookAsync(@PathVariable Long storeBookId) {
        long startTime = System.currentTimeMillis();
        Long userId = UserContextHolder.getUserId();
        log.info("⚡ [异步购买测试] 开始 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
        Long userBookId = purchaseService.purchaseBookAsync(userId, storeBookId);
        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ [异步购买测试] 完成 | 用户ID: {} | 商店书ID: {} | 关键路径耗时: {}ms",
                userId, storeBookId, duration);
        return Result.success("异步购买成功，后台正在处理单词复制等操作", userBookId);
    }
}
