package org.example.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.annotation.MethodRunTime;
import org.example.common.PageResult;
import org.example.config.RabbitMQConfig;
import org.example.mq.producer.MessageProducer;
import org.example.context.UserContextHolder;
import org.example.dto.FlashSaleDTO;
import org.example.dto.StoreBookQueryDTO;
import org.example.entity.*;
import org.example.mapper.*;
import org.example.service.StoreService;
import org.example.constant.RedisKeys;
import org.example.exception.RateLimitException;
import org.example.utils.RateLimiter;
import org.example.utils.RedisUtil;
import org.example.vo.StoreBookVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商店服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StoreServiceImpl implements StoreService {

    private final StoreProductMapper storeProductMapper;
    private final StorePurchaseRecordMapper storePurchaseRecordMapper;
    private final UserVocabularyBookMapper userVocabularyBookMapper;
    private final UserBookWordMapper userBookWordMapper;
    private final PublicBookWordMapper publicBookWordMapper;
    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final PublicVocabularyBookMapper publicVocabularyBookMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final RabbitMQConfig rabbitMQConfig;
    private final MessageProducer messageProducer;
    private final SeckillMessageLogMapper seckillMessageLogMapper;
    private final RateLimiter rateLimiter;

    @Override
    @MethodRunTime("商店service层-查询图书列表")
    public PageResult<StoreBookVO> queryStoreBooks(StoreBookQueryDTO queryDTO) {
        log.debug("📚 [商店] 查询单词书列表 | 分类: {} | 难度: {} | 页码: {} | 每页: {}",
                queryDTO.getCategory(), queryDTO.getDifficulty(), queryDTO.getPage(), queryDTO.getSize());

        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();
        int limit = queryDTO.getSize();

        // 构建排序，注意加表别名避免歧义
        String orderBy = "sp.sort_order DESC, sp.created_at DESC";
        if ("price".equals(queryDTO.getSortBy())) {
            orderBy = "sp.price ASC";
        } else if ("hot".equals(queryDTO.getSortBy())) {
            orderBy = "sp.is_hot DESC, sp.sort_order DESC";
        } else if ("new".equals(queryDTO.getSortBy())) {
            orderBy = "sp.created_at DESC";
        }

        // 带条件的分页查询
        long total = storeProductMapper.countByFilter(queryDTO.getCategory(), queryDTO.getDifficulty());
        List<StoreProduct> productList = storeProductMapper.selectByFilter(
                queryDTO.getCategory(), queryDTO.getDifficulty(), orderBy, limit, offset);

        // 转换为VO
        long userId = UserContextHolder.getUserId();
        List<Long> userBuyBooks = userMapper.selectPurchasedProducts(userId);
        List<StoreBookVO> voList = productList.stream()
                .map(product -> {
                    StoreBookVO vo = new StoreBookVO();
                    BeanUtils.copyProperties(product, vo);
                    vo.setBookName(product.getProductName());

                    // 查询公共单词书获取分类和单词数量
                    PublicVocabularyBook book = publicVocabularyBookMapper.selectById(product.getReferenceId());
                    if (book != null) {
                        vo.setCategory(book.getCategory());
                        vo.setDifficulty(book.getDifficulty());
                        vo.setWordCount(book.getWordCount());
                    }

                    vo.setIsPurchased(userBuyBooks.contains(product.getId()));
                    // 计算折扣率（百分比）
                    if (product.getOriginalPrice() != null && product.getOriginalPrice() > 0) {
                        double discountRate = (product.getPrice() * 100.0) / product.getOriginalPrice();
                        vo.setDiscount((double) Math.round(discountRate));
                    } else {
                        vo.setDiscount(null);
                    }

                    // 从 Redis 获取实时销售计数（覆盖 MySQL 的旧值）
                    String salesKey = RedisKeys.storeSales(product.getId());
                    Object redisSales = redisUtil.get(salesKey);
                    if (redisSales != null) {
                        vo.setSalesCount(((Number) redisSales).intValue());
                    }

                    return vo;
                })
                .collect(Collectors.toList());

        PageResult<StoreBookVO> pageResult = new PageResult<>(total, queryDTO.getPage(), queryDTO.getSize(), voList);
        log.debug("✅ [商店] 查询成功 | 总数: {} | 当前页: {} | 返回: {} 条",
                pageResult.getTotal(), pageResult.getCurrent(), voList.size());

        return pageResult;
    }
    
    @Override
    public StoreBookVO getBookDetail(Long id) {
        log.debug("🔍 [商店] 查询单词书详情 | ID: {}", id);
        
        StoreProduct product = storeProductMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        
        return convertToVO(product);
    }
    
    @Override
    public Boolean isPurchased(Long userId, Long productId) {
        log.debug("💾 [DB] 从数据库检查用户是否已购买 | 用户ID: {} | 商品ID: {}", userId, productId);
        StorePurchaseRecord record = storePurchaseRecordMapper.selectByUserAndProduct(userId, productId);
        return record != null;
    }

    @Override
    public List<PublicWord> queryBookWords(Long id) {
        log.debug("💾 [DB] 从数据库查询单词书的单词列表 | 单词书ID: {}", id);
        return storeProductMapper.selectWords(id);
    }

    @Override
    public Integer queryBookWord(Long id) {
        return 0;
    }
    @PostConstruct
    public void initSeckillCache() {
        log.info("🚀 [系统启动] 开始预热秒杀活动缓存...");
        try {
            List<SeckillActivity> activities = seckillActivityMapper.selectAllActivities();
            if (activities != null && !activities.isEmpty()) {
                for (SeckillActivity activity : activities) {
                    // 1. 预热库存（确保转为 Long 类型）
                    String stockKey = RedisKeys.seckillStock(activity.getId());
                    Long stockValue = (long) activity.getStock();
                    redisUtil.set(stockKey, stockValue, 24, TimeUnit.HOURS);
                    
                    // 2. 验证写入是否成功
                    Object verifyStock = redisUtil.get(stockKey);
                    log.info("✅ [缓存预热] 活动ID: {} | 初始库存: {} | Redis验证: {}", 
                            activity.getId(), stockValue, verifyStock);
                }
                log.info("🎉 [缓存预热] 完成！共预热 {} 个秒杀活动", activities.size());
            } else {
                log.warn("⚠️ [缓存预热] 数据库中暂无秒杀活动数据，请检查 seckill_activity 表");
            }
        } catch (Exception e) {
            log.error("❌ [缓存预热] 失败", e);
        }
    }

    @Override
    @Transactional
    public Long purchaseBook(Long userId, Long storeBookId) {
        log.info("🛒 [购买] 开始处理 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
        
        // 1. 检查是否已购买
        if (isPurchased(userId, storeBookId)) {
            log.warn("⚠️ [购买] 用户已购买 | 用户ID: {} | 商店书ID: {}", userId, storeBookId);
            throw new RuntimeException("您已经购买过该单词书，无需重复购买");
        }
        
        // 2. 获取商品信息
        log.debug("💾 [DB] 从数据库查询商品信息 | 商店书ID: {}", storeBookId);
        StoreProduct product = storeProductMapper.selectById(storeBookId);
        if (product == null) {
            log.error("❌ [购买] 商品不存在 | 商店书ID: {}", storeBookId);
            throw new RuntimeException("商品不存在");
        }
        
        // 3. 检查积分余额
        String key = RedisKeys.userPoints(userId);
        log.debug("🔍 [Redis] 从 Redis 获取用户积分余额 | Key: {}", key);
        Object balanceObj = redisUtil.get(key);
        if (balanceObj == null) {
            log.error("❌ [购买] 用户积分账户不存在 | 用户ID: {}", userId);
            throw new RuntimeException("用户积分账户不存在");
        }
        
        long balance = ((Number) balanceObj).longValue();
        log.debug("✅ [Redis] 获取用户积分余额成功 | 余额: {}", balance);
        if (balance < product.getPrice()) {
            log.warn("⚠️ [购买] 积分不足 | 用户ID: {} | 余额: {} | 需要: {}", userId, balance, product.getPrice());
            throw new RuntimeException("积分不足，当前余额: " + balance + "，需要: " + product.getPrice());
        }
        
        // 4. 扣除积分
        redisUtil.decrement(key, product.getPrice());
        log.info("💰 [购买] 扣除积分 | 用户ID: {} | 扣除: {} | 剩余: {}", userId, product.getPrice(), balance - product.getPrice());
        
        // 5. 创建用户单词书
        log.info("💾 [DB] 准备创建用户单词书到数据库");
        UserVocabularyBook userBook = new UserVocabularyBook();
        userBook.setUserId(userId);
        userBook.setBookName(product.getProductName());
        userBook.setDescription(product.getDescription());
        userBook.setCoverImage(product.getCoverImage());
        userBook.setWordCount(0); // 初始为0，后面会更新
        userBook.setIsPublic(0); // 默认为私有
        userBook.setSourceType(2); // 2-从商店购买
        userBook.setSourceStoreBookId(storeBookId);
        userBook.setCreatedAt(LocalDateTime.now());
        userBook.setUpdatedAt(LocalDateTime.now());
        
        userVocabularyBookMapper.insert(userBook);
        Long userBookId = userBook.getId();
        log.info("📚 [购买] 创建用户单词书 | 用户书ID: {}", userBookId);
        
        // 6. 复制单词关联（从公共单词书到用户单词书）
        log.debug("💾 [DB] 从数据库查询公共单词书的单词关联 | 商店书ID: {}", storeBookId);
        List<PublicBookWord> publicBookWords = publicBookWordMapper.selectByBookId(storeBookId);
        if (publicBookWords != null && !publicBookWords.isEmpty()) {
            List<UserBookWord> userBookWords = new ArrayList<>();
            for (PublicBookWord publicBookWord : publicBookWords) {
                UserBookWord userBookWord = new UserBookWord();
                userBookWord.setUserId(userId);
                userBookWord.setBookId(userBookId);
                userBookWord.setWordId(publicBookWord.getWordId());
                userBookWord.setMastered(0); // 未掌握
                userBookWord.setReviewCount(0); // 复习次数为0
                userBookWord.setDifficulty(1); // 默认简单
                userBookWord.setPriority(publicBookWord.getSortOrder()); // 使用排序作为优先级
                userBookWord.setCreatedAt(LocalDateTime.now());
                userBookWord.setUpdatedAt(LocalDateTime.now());
                userBookWords.add(userBookWord);
            }
            
            // 批量插入用户单词关联
            userBookWordMapper.batchInsert(userBookWords);
            
            // 更新单词书的单词数量
            int wordCount = userBookWords.size();
            userVocabularyBookMapper.updateWordCount(userBookId, wordCount);
            
            log.info("📋 [购买] 复制单词完成 | 用户书ID: {} | 单词数量: {}", userBookId, wordCount);
        } else {
            log.warn("⚠️ [购买] 公共单词书没有单词 | 商店书ID: {}", storeBookId);
        }
        
        // 7. 记录购买记录
        StorePurchaseRecord purchaseRecord = new StorePurchaseRecord();
        purchaseRecord.setUserId(userId);
        purchaseRecord.setProductId(storeBookId);
        purchaseRecord.setPricePaid(product.getPrice());
        purchaseRecord.setPurchaseType(1); // 1-正常购买
        purchaseRecord.setUserBookId(userBookId);
        purchaseRecord.setCreatedAt(LocalDateTime.now());
        
        storePurchaseRecordMapper.insert(purchaseRecord);
        log.info("📝 [购买] 记录购买记录 | 记录ID: {}", purchaseRecord.getId());
        
        // 8. 更新商品销售数量
        Integer currentSales = product.getSalesCount() != null ? product.getSalesCount() : 0;
        storeProductMapper.updateSalesCount(storeBookId, currentSales + 1);
        redisUtil.increment(RedisKeys.storeSales(storeBookId), 1);

        // 9. 异步落库积分扣减
        String orderNo = "PURCHASE_" + userId + "_" + storeBookId + "_" + System.currentTimeMillis();
        messageProducer.SendPointDeductMessage(userId, product.getPrice(), orderNo);
        log.info("📤 [购买] 发送积分落库消息 | 订单号: {}", orderNo);

        log.info("✅ [购买] 购买完成 | 用户ID: {} | 商店书ID: {} | 用户书ID: {}", userId, storeBookId, userBookId);

        return userBookId;
    }
    
    /**
     * 转换为VO
     */
    private StoreBookVO convertToVO(StoreProduct product) {
        StoreBookVO vo = new StoreBookVO();
        BeanUtils.copyProperties(product, vo);
        vo.setBookName(product.getProductName());

        // 查询公共单词书获取分类和单词数量
        PublicVocabularyBook book = publicVocabularyBookMapper.selectById(product.getReferenceId());
        if (book != null) {
            vo.setCategory(book.getCategory());
            vo.setDifficulty(book.getDifficulty());
            vo.setWordCount(book.getWordCount());
        }

        // 计算折扣率
        if (product.getOriginalPrice() != null && product.getOriginalPrice() > 0) {
            double discount = (product.getPrice() * 100.0) / product.getOriginalPrice();
            vo.setDiscount((double)Math.round( discount));
        }

        return vo;
    }

    @Override
    public List<FlashSaleDTO> queryFlashSaleList() {
        long startTime = System.currentTimeMillis();
        log.info("⚡ [秒杀] 查询秒杀商品列表");
        
        // 1. 查询所有秒杀活动
        List<SeckillActivity> activities = seckillActivityMapper.selectAllActivities();
        if (activities == null || activities.isEmpty()) {
            log.debug("✅ [秒杀] 暂无秒杀活动");
            return new ArrayList<>();
        }
        
        log.debug("📋 [秒杀] 查询到 {} 个秒杀活动", activities.size());
        
        // 2. 转换为 DTO
        LocalDateTime now = LocalDateTime.now();
        List<FlashSaleDTO> flashSaleList = activities.stream()
                .map(activity -> {
                    FlashSaleDTO dto = new FlashSaleDTO();
                    
                    // 设置秒杀活动基本信息
                    dto.setId(activity.getId());
                    dto.setFlashPrice(activity.getSeckillPrice());
                    dto.setStock(activity.getStock());
                    dto.setStartTime(activity.getStartTime());
                    dto.setEndTime(activity.getEndTime());
                    
                    // 判断状态
                    String status;
                    if (now.isBefore(activity.getStartTime())) {
                        status = "upcoming"; // 未开始
                    } else if (now.isAfter(activity.getEndTime())) {
                        status = "ended"; // 已结束
                    } else {
                        status = "ongoing"; // 进行中
                    }
                    dto.setStatus(status);
                    
                    // 3. 查询关联的商品信息
                    StoreProduct product = storeProductMapper.selectById(activity.getProductId());
                    if (product != null) {
                        dto.setBookId(product.getReferenceId());
                        dto.setBookName(product.getProductName());
                        dto.setCoverImage(product.getCoverImage());
                        dto.setOriginalPrice(product.getOriginalPrice());
                        dto.setDescription(product.getDescription());
                        // 4. 从 Redis 获取实时销售计数
                        String salesKey = RedisKeys.storeSales(product.getId());
                        Object redisSales = redisUtil.get(salesKey);
                        dto.setSoldCount(redisSales != null ? ((Number) redisSales).intValue() : 0);
                        // 5. 查询单词书的详细信息（单词数量、难度等）
                        if (product.getReferenceId() != null) {
                            PublicVocabularyBook book = publicVocabularyBookMapper.selectById(product.getReferenceId());
                            if (book != null) {
                                dto.setWordCount(book.getWordCount());
                                dto.setDifficulty(book.getDifficulty());
                            }
                        }
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
        
        log.info("✅ [秒杀] 查询成功 | 共 {} 个秒杀商品", flashSaleList.size());
        log.info("⚡ [秒杀] 秒杀商品列表: {}", flashSaleList);
        log.info("⚡ [秒杀] 耗时: {} ms", System.currentTimeMillis() - startTime);
        return flashSaleList;
    }

    @Override
    @MethodRunTime("秒杀service层-秒杀方法耗时")
    @Transactional(rollbackFor = Exception.class)
    public Long flashsale(Long userId, Long id) {
        log.info("⚡ [秒杀核心] 开始处理 | 用户ID: {} | 活动ID: {}", userId, id);

        // 0. 校验活动时间和商品信息
        SeckillActivity activity = seckillActivityMapper.selectById(id);
        if (activity == null) {
            throw new RuntimeException("秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new RuntimeException("秒杀尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new RuntimeException("秒杀已结束");
        }

        // 0.5 限流拦截（在 Redis 操作之前）
        // 第一层：用户级限流，每人每秒最多 3 次，防恶意刷接口
        if (!rateLimiter.tryAcquireSeckillUser(userId, id)) {
            log.warn("🚫 [秒杀] 用户触发频率限制 | 用户ID: {} | 活动ID: {}", userId, id);
            throw new RateLimitException(RateLimitException.TYPE_USER, "操作太频繁，请稍后重试");
        }
        // 第二层：全局限流，令牌桶容量 300 / 每秒填充 200，保护后端
        if (!rateLimiter.tryAcquireSeckillGlobal()) {
            log.warn("🚫 [秒杀] 系统触发全局限流 | 用户ID: {} | 活动ID: {}", userId, id);
            throw new RateLimitException(RateLimitException.TYPE_GLOBAL, "活动太火爆，请稍后重试");
        }

        // 调试：检查 Redis 中是否有库存数据
        Object stockObj = redisUtil.get(RedisKeys.seckillStock(id));
        log.debug("🔍 [调试] Redis 库存Key: {} | 当前值: {}", RedisKeys.seckillStock(id), stockObj);

        if (stockObj == null) {
            log.error("❌ [秒杀失败] Redis 中未找到库存数据，请检查预热是否成功 | 活动ID: {}", id);
            throw new RuntimeException("秒杀活动未就绪，请稍后重试");
        }

        // 1. 预扣减库存（先扣库存，再检查幂等性）
        String stockKey = RedisKeys.seckillStock(id);
        Long remaining = redisUtil.decrement(stockKey, 1);
        log.debug("📊 [库存扣减] 活动ID: {} | 扣减后剩余: {}", id, remaining);

        if (remaining == null || remaining < 0) {
            redisUtil.increment(stockKey, 1);
            log.warn("⚠️ [秒杀拦截] 库存不足 | 活动ID: {} | 剩余: {}", id, remaining);
            throw new RuntimeException("库存已抢光");
        }

        // 2. 幂等性校验：一人一单 (SETNX)
        String userKey = RedisKeys.seckillUser(userId, id);
        Boolean isFirst = redisUtil.setIfAbsent(userKey, "1", 1, TimeUnit.HOURS);
        if (!isFirst) {
            redisUtil.increment(stockKey, 1);
            log.warn("⚠️ [秒杀拦截] 用户重复请求 | 用户ID: {}", userId);
            throw new RuntimeException("您已经抢购过了，请勿重复提交");
        }

        // 3. 检查积分余额（从 Redis 缓存查，快速拦截）
        String pointsKey = RedisKeys.userPoints(userId);
        Object balanceObj = redisUtil.get(pointsKey);
        if (balanceObj != null) {
            long balance = ((Number) balanceObj).longValue();
            if (balance < activity.getSeckillPrice()) {
                redisUtil.increment(stockKey, 1);
                redisUtil.delete(userKey);
                log.warn("⚠️ [秒杀拦截] 积分不足 | 用户ID: {} | 余额: {} | 需要: {}",
                        userId, balance, activity.getSeckillPrice());
                throw new RuntimeException("积分不足");
            }
        }

        // 4. 创建订单记录
        String orderNo = "SK" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setActivityId(id);
        order.setOrderNo(orderNo);
        seckillOrderMapper.insert(order);

        // 5. MySQL 库存扣减（乐观锁，兜底）
        int affectedRows = seckillActivityMapper.deductStock(id);
        if (affectedRows <= 0) {
            // 回滚 Redis 库存和幂等性标记，抛出异常触发 @Transactional 回滚
            redisUtil.increment(stockKey, 1);
            redisUtil.delete(userKey);
            log.warn("⚠️ [秒杀] MySQL 库存扣减失败，已回滚 | 活动ID: {}", id);
            throw new RuntimeException("库存已抢光");
        }

        // 6. Redis + MySQL 销售计数更新
        if (activity.getProductId() != null) {
            redisUtil.increment(RedisKeys.storeSales(activity.getProductId()), 1);
            StoreProduct sp = storeProductMapper.selectById(activity.getProductId());
            if (sp != null) {
                int newSales = (sp.getSalesCount() != null ? sp.getSalesCount() : 0) + 1;
                storeProductMapper.updateSalesCount(activity.getProductId(), newSales);
            }
        }

        // 7. 写幂等性日志 + 发送 MQ 异步消息
        sendFlashSaleMqMessage(userId, id, orderNo, activity.getSeckillPrice(), activity.getProductId());

        log.info("✅ [秒杀成功] 订单已生成 | 用户ID: {} | 活动ID: {} | 订单号: {}", userId, id, orderNo);
        return order.getId();
    }

    /**
     * 发送秒杀异步消息（先写 SeckillMessageLog，再发 MQ，防止重复消费）
     */
    private void sendFlashSaleMqMessage(Long userId, Long activityId, String orderNo, Integer price, Long productId) {
        // 写幂等性日志（status=0 待消费）
        SeckillMessageLog messageLog = new SeckillMessageLog();
        messageLog.setMessageId(orderNo);
        messageLog.setMessageContent(String.format("userId=%d,activityId=%d,price=%d,productId=%d", userId, activityId, price, productId));
        messageLog.setStatus(0);
        seckillMessageLogMapper.insert(messageLog);

        // 发 MQ 消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("userId", userId);
        msg.put("activityId", activityId);
        msg.put("orderNo", orderNo);
        msg.put("productId", productId);
        messageProducer.sendSeckillMessage(msg);
        log.debug("📤 [秒杀] 异步消息已发送 | 订单号: {}", orderNo);
    }
}
