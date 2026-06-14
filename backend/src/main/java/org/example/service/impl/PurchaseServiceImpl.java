package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.*;
import org.example.mapper.PublicBookWordMapper;
import org.example.mapper.PublicVocabularyBookMapper;
import org.example.mapper.StoreProductMapper;
import org.example.mapper.StorePurchaseRecordMapper;
import org.example.mq.producer.MessageProducer;
import org.example.service.*;
import org.example.constant.RedisKeys;
import org.example.utils.RedisUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购买服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PurchaseServiceImpl implements PurchaseService {

    private final StoreProductMapper storeProductMapper;
    private final StorePurchaseRecordMapper storePurchaseRecordMapper;
    private final UserPointsAccountService userPointsAccountService;
    private final UserVocabularyBookService userVocabularyBookService;
    private final UserBookWordService userBookWordService;
    private final PublicBookWordMapper publicBookWordMapper;
    private final MessageProducer messageProducer;
    private final PublicVocabularyBookMapper publicVocabularyBookMapper;
    private final RedisUtil redisUtil;

    /**
     * 同步处理用户购买逻辑
     * 所有步骤都在主线程中完成，响应时间包含所有操作
     */
    @Override
    @Transactional
    public Long purchaseBookSync(Long userId, Long productId) {
        return purchaseBookAsync(userId, productId);
    }

    /**
     * 异步处理用户购买逻辑（使用 RabbitMQ 处理非关键路径）
     * 只处理关键路径，非关键操作通过消息队列异步处理
     */
    @Override
    @Transactional
    public Long purchaseBookAsync(Long userId, Long productId) {
        long startTime = System.currentTimeMillis();
        long stepStart;
        log.info("⏱️ [异步购买-总] 开始 | 用户ID: {} | 商品ID: {}", userId, productId);

        // 1. 检查是否已购买
        stepStart = System.currentTimeMillis();
        if (isPurchased(userId, productId)) {
            throw new RuntimeException("您已经购买过该单词书，无需重复购买");
        }
        log.info("⏱️ [异步购买-步骤1] 检查购买状态 | 耗时: {}ms", System.currentTimeMillis() - stepStart);

        // 2. 获取商品信息
        stepStart = System.currentTimeMillis();
        StoreProduct product = storeProductMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        log.info("⏱️ [异步购买-步骤2] 查询商品信息 | 耗时: {}ms", System.currentTimeMillis() - stepStart);

        // 3. 检查积分余额并扣除（关键路径，必须同步完成）
        stepStart = System.currentTimeMillis();
        userPointsAccountService.deductPoints(
                userId,
                product.getPrice(),
                4, // 类型：4-购买消费
                "购买商品: " + product.getProductName(),
                productId
        );
        log.info("⏱️ [异步购买-步骤3] 扣除积分 | 耗时: {}ms", System.currentTimeMillis() - stepStart);

        // 4. 创建用户单词书（关键路径，需要立即返回ID给用户）
        stepStart = System.currentTimeMillis();
        PublicVocabularyBook publicBook = publicVocabularyBookMapper.selectById(product.getReferenceId());

        Long userBookId = userVocabularyBookService.createVocabularyBook(
                userId,
                product.getProductName(),
                product.getDescription(),
                product.getCoverImage()
        );
        log.info("⏱️ [异步购买-步骤4] 创建用户单词书 | 用户书ID: {} | 耗时: {}ms", userBookId, System.currentTimeMillis() - stepStart);

        // 5. 记录购买记录（关键路径）
        stepStart = System.currentTimeMillis();
        StorePurchaseRecord purchaseRecord = new StorePurchaseRecord();
        purchaseRecord.setUserId(userId);
        purchaseRecord.setProductId(productId);
        purchaseRecord.setPricePaid(product.getPrice());
        purchaseRecord.setPurchaseType(1); // 1-正常购买
        purchaseRecord.setUserBookId(userBookId);
        purchaseRecord.setCreatedAt(LocalDateTime.now());

        storePurchaseRecordMapper.insert(purchaseRecord);
        log.info("⏱️ [异步购买-步骤5] 记录购买记录 | 耗时: {}ms", System.currentTimeMillis() - stepStart);

        // 6. 发送异步消息处理非关键路径操作（复制单词关联、更新统计等）
        stepStart = System.currentTimeMillis();
        sendAsyncPurchaseMessage(userId, productId, userBookId, product.getPrice(), publicBook != null ? publicBook.getId() : null);
        log.info("⏱️ [异步购买-步骤6] 发送异步消息 | 耗时: {}ms", System.currentTimeMillis() - stepStart);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        redisUtil.delete(RedisKeys.userBooks(userId));
        log.info("✅ [异步购买-总计] 关键路径完成 | 用户ID: {} | 商品ID: {} | 总耗时: {}ms",
                userId, productId, duration);
        return userBookId;
    }

    /**
     * 发送异步购买消息到 RabbitMQ
     */
    private void sendAsyncPurchaseMessage(Long userId, Long productId, Long userBookId, Integer pricePaid, Long publicBookId) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("productId", productId);
        message.put("userBookId", userBookId);
        message.put("publicBookId", publicBookId);
        message.put("pricePaid", pricePaid);
        message.put("timestamp", System.currentTimeMillis());

        try {
            messageProducer.sendPurchaseMessage(message);
            log.info("📤 [异步购买] 发送消息成功 | 用户ID: {} | 商品ID: {} | 用户书ID: {}",
                    userId, productId, userBookId);
        } catch (Exception e) {
            log.error("❌ [异步购买] 发送消息失败 | 用户ID: {} | 错误: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 检查用户是否已购买
     */
    private Boolean isPurchased(Long userId, Long productId) {
        StorePurchaseRecord record = storePurchaseRecordMapper.selectByUserAndProduct(userId, productId);
        return record != null;
    }

    /**
     * 复制单词关联（从公共单词书到用户单词书）
     */
    private void copyWordsFromPublicToUser(Long publicBookId, Long userBookId, Long userId) {
        log.info("📋 [复制单词] 开始 | 公共书ID: {} | 用户书ID: {}", publicBookId, userBookId);

        List<PublicBookWord> publicBookWords = publicBookWordMapper.selectByBookId(publicBookId);

        if (publicBookWords == null || publicBookWords.isEmpty()) {
            log.warn("⚠️ [复制单词] 公共单词书没有单词 | 公共书ID: {}", publicBookId);
            return;
        }

        int count = userBookWordService.batchAddWordsToBook(userId, userBookId, publicBookWords);

        log.info("✅ [复制单词] 完成 | 公共书ID: {} | 用户书ID: {} | 复制数量: {}",
                publicBookId, userBookId, count);
    }
}
