package org.example.constant;

/**
 * Redis 键常量 — 集中管理所有 Redis key 模式
 * <p>
 * 命名规范：{@code wf:模块:对象:标识}
 * 所有业务模块统一从这里引用，避免魔法字符串散落在各处。
 */
public final class RedisKeys {

    private RedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 用户缓存 ====================

    /** 用户信息缓存: wf:cache:user:{userId} */
    private static final String PREFIX_CACHE_USER = "wf:cache:user:";
    public static String cacheUser(Long userId) {
        return PREFIX_CACHE_USER + userId;
    }

    /** 用户积分余额: wf:cache:points:{userId} */
    private static final String PREFIX_CACHE_POINTS = "wf:cache:points:";
    public static String userPoints(Long userId) {
        return PREFIX_CACHE_POINTS + userId;
    }


    // ==================== 用户单词本 ====================

    /** 用户单词本列表: wf:cache:wordbook:list:{userId} */
    private static final String PREFIX_BOOK_LIST = "wf:cache:wordbook:list:";
    public static String userBooks(Long userId) {
        return PREFIX_BOOK_LIST + userId;
    }

    /** {@link #userBooks(Long)} 的别名，语义更清晰 */
    public static String cacheVocabBooks(Long userId) {
        return userBooks(userId);
    }

    /** 用户单词本内单词列表: wf:cache:wordbook:{bookId}:words */
    private static final String PREFIX_BOOK_WORDS = "wf:cache:wordbook:";
    public static String userWordList(Long userId, Long bookId) {
        return PREFIX_BOOK_WORDS + bookId + ":words";
    }

    /** {@link #userWordList(Long, Long)} 的别名 */
    public static String cacheWords(Long bookId) {
        return PREFIX_BOOK_WORDS + bookId + ":words";
    }


    // ==================== 秒杀系统 ====================

    /** 秒杀库存: wf:stock:flash:{activityId} */
    private static final String PREFIX_SECKILL_STOCK = "wf:stock:flash:";
    public static String seckillStock(Long activityId) {
        return PREFIX_SECKILL_STOCK + activityId;
    }

    /** 秒杀分布式锁: wf:lock:flash:{activityId} */
    private static final String PREFIX_SECKILL_LOCK = "wf:lock:flash:";
    public static String seckillLock(Long activityId) {
        return PREFIX_SECKILL_LOCK + activityId;
    }

    /** 秒杀幂等拦截（防重复下单）: wf:idempotent:order:{userId}:{activityId} */
    private static final String PREFIX_SECKILL_USER = "wf:idempotent:order:";
    public static String seckillUser(Long userId, Long activityId) {
        return PREFIX_SECKILL_USER + userId + ":" + activityId;
    }


    // ==================== 商店销售计数 ====================

    /** 商店销售计数: wf:store:sales:{productId} */
    private static final String PREFIX_STORE_SALES = "wf:store:sales:";
    public static String storeSales(Long productId) {
        return PREFIX_STORE_SALES + productId;
    }
}
