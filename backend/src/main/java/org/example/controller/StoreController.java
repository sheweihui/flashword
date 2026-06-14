package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.context.UserContextHolder;
import org.example.dto.StoreBookQueryDTO;
import org.example.dto.FlashSaleDTO;
import org.example.entity.PublicWord;
import org.example.entity.UserPointsAccount;
import org.example.service.UserCheckinService;
import org.example.service.UserPointsAccountService;
import org.example.service.StoreService;
import org.example.vo.CheckinVO;
import org.example.vo.PointsVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商店控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final UserPointsAccountService userPointsAccountService;
    private final StoreService storeService;
    private final UserCheckinService userCheckinService;

    @GetMapping("/points/balance")
    public Result<PointsVO> getPointsBalance() {
        Long userId = UserContextHolder.getUserId();
        UserPointsAccount account = userPointsAccountService.getAccountByUserId(userId);
        if (account == null) {
            account = userPointsAccountService.createAccount(userId);
        }
        PointsVO vo = new PointsVO();
        BeanUtils.copyProperties(account, vo);
        log.debug("📊 [积分查询] 用户ID: {} | 余额: {}", userId, account.getBalance());
        return Result.success(vo);
    }

    @PostMapping("/checkin")
    public Result<CheckinVO> checkin() {
        Long userId = UserContextHolder.getUserId();
        CheckinVO result = userCheckinService.checkin(userId);
        if (!result.getCheckedIn()) {
            return Result.success("今日已签到", result);
        }
        String msg = String.format("签到成功！获得%d积分，连续签到%d天",
                result.getPointsEarned(), result.getContinuousDays());
        return Result.success(msg, result);
    }

    @GetMapping("/books")
    public Result queryStoreBooks(StoreBookQueryDTO queryDTO) {
        log.info("📚 [REQUEST] GET /api/store/books | 用户: {}", UserContextHolder.getUserId());
        return Result.success(storeService.queryStoreBooks(queryDTO));
    }

    @GetMapping("/books/{id}")
    public Result getBookDetail(@PathVariable Long id) {
        log.debug("🔍 [商店] 查询单词书详情 | ID: {}", id);
        return Result.success(storeService.getBookDetail(id));
    }

    @PostMapping("/books/{id}/purchase")
    public Result purchaseBook(@PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        log.info("🛒 [购买] 用户ID: {} | 单词书ID: {}", userId, id);
        Long bookId = storeService.purchaseBook(userId, id);
        return Result.success("购买成功", bookId);
    }

    @GetMapping("/books/{id}/words")
    public Result<List<PublicWord>> queryBookWords(@PathVariable Long id) {
        log.info("💾 [DB] 从数据库查询单词书的单词列表 | 单词书ID: {}", id);
        return Result.success(storeService.queryBookWords(id));
    }

    @GetMapping("/flash-sale/list")
    public Result<List<FlashSaleDTO>> queryFlashSaleList() {
        log.info("⚡ [秒杀] 查询秒杀列表");
        return Result.success(storeService.queryFlashSaleList());
    }

    @PostMapping("/flash-sale/purchase/{id}")
    public Result<Long> purchaseFlashSaleBook(@PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        log.info("⚡ [秒杀] 秒杀购买 | 用户ID: {} | 秒杀ID: {}", userId, id);
        return Result.success("秒杀成功", storeService.flashsale(userId, id));
    }
}
