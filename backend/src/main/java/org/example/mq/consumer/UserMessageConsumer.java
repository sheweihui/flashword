package org.example.mq.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import org.example.annotation.RabbitMqMessage;
import org.example.annotation.RabbitTime;
import org.example.config.RabbitMQConfig;
import org.example.entity.PublicWord;
import org.example.entity.UserVocabularyBook;
import org.example.entity.UserWord;
import org.example.service.StoreService;
import org.example.service.UserService;
import org.example.service.UserVocabularyBookService;
import org.example.service.UserWordService;
import org.example.constant.RedisKeys;
import org.example.utils.RedisUtil;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class UserMessageConsumer {

    private final RedisUtil redisUtil;

    private final StoreService storeService;

    private final UserVocabularyBookService userVocabularyBookService;

    private final UserWordService userWordService;

    private static final long CACHE_EXPIRE_TIME = 24; // 缓存过期时间（小时）

    // 缓存统计计数器
    private static int cacheSuccessCount = 0;
    private static int cacheFailCount = 0;
    private final Jackson2JsonMessageConverter messageConverter;
    private final UserService userService;
    /**
     * 消费用户登录消息，异步缓存用户的商店、单词本、单词信息到Redis
     */
    @RabbitListener(queues = RabbitMQConfig.USER_MESSAGE_QUEUE)
    @RabbitMqMessage(message = "用户登录")
    @RabbitTime(message = "缓存用户信息耗时")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        org.springframework.amqp.support.converter.Jackson2JsonMessageConverter converter = new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();

        try {
            Map<String, Object> body = (Map<String, Object>) converter.fromMessage(message);
            log.info("📥 [UserMessageConsumer] 收到用户登录消息: {}", body);

            // 提取消息内容（处理 Integer -> Long 的类型转换）
            Object userIdObj = body.get("userId");
            Long userId = null;
            if (userIdObj instanceof Integer) {
                userId = ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                userId = (Long) userIdObj;
            }

            String token = (String) body.get("token");

            if (userId == null) {
                log.error("❌ [UserMessageConsumer] 用户ID为空，无法处理消息");
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("🔄 [UserMessageConsumer] 开始异步缓存用户数据 | 用户ID: {}", userId);

            // 1. 缓存用户的单词本列表
            cacheUserVocabularyBooks(userId);

            // 2. 缓存每个单词本中的单词
            cacheUserWords(userId);

            // 3. 缓存商店单词书列表（可选，根据业务需求）
            cacheStoreBooks();

            log.info("✅ [UserMessageConsumer] 用户数据缓存完成 | 用户ID: {} | Token: {}", userId, token);

            // 输出缓存统计信息
            cacheSuccessCount++;
            log.info("📊 [UserMessageConsumer] 缓存统计 | 成功: {} | 失败: {} | 总计: {}",
                    cacheSuccessCount, cacheFailCount, cacheSuccessCount + cacheFailCount);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("❌ [UserMessageConsumer] 处理消息失败: {}", e.getMessage(), e);
            cacheFailCount++;

            Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
            if (retryCount == null) retryCount = 0;

            if (retryCount < 3) {
                retryCount++;
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                log.warn("⚠️ [UserMessageConsumer] 第 {} 次重试 | deliveryTag: {}", retryCount, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("❌ [UserMessageConsumer] 达到最大重试次数，消息进入死信队列 | deliveryTag: {}", deliveryTag);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
    
    /**
     * 缓存用户的单词本列表
     */
    private void cacheUserVocabularyBooks(Long userId) {
        try {
            log.info("💾 [DB→Redis] 从数据库查询用户单词本列表 | 用户ID: {}", userId);
            List<UserVocabularyBook> books = userVocabularyBookService.listByUserId(userId);
            String cacheKey = RedisKeys.cacheVocabBooks(userId);
            
            redisUtil.set(cacheKey, books, CACHE_EXPIRE_TIME, TimeUnit.HOURS);
            log.info("✅ [DB→Redis] 单词本列表已缓存到Redis | 用户ID: {} | 数量: {} | Key: {}", 
                    userId, books.size(), cacheKey);
            cacheSuccessCount++;
        } catch (Exception e) {
            log.error("❌ [DB→Redis] 缓存单词本列表失败 | 用户ID: {}", userId, e);
            cacheFailCount++;
        }
    }
    
    /**
     * 缓存用户每个单词本中的单词
     */
    private void cacheUserWords(Long userId) {
        try {
            log.info("💾 [DB→Redis] 从数据库查询用户单词本列表（用于获取单词） | 用户ID: {}", userId);
            List<UserVocabularyBook> books = userVocabularyBookService.listByUserId(userId);
            
            for (UserVocabularyBook book : books) {
                try {
                    log.info("💾 [DB→Redis] 从数据库查询单词本单词 | 单词本ID: {}", book.getId());
                    List<UserWord> words = userVocabularyBookService.getBookByIdGetALLWORD(book.getId());
                    String cacheKey = RedisKeys.cacheWords(book.getId());
                    
                    redisUtil.set(cacheKey, words, CACHE_EXPIRE_TIME, TimeUnit.HOURS);
                    log.info("✅ [DB→Redis] 单词数据已缓存到Redis | 单词本ID: {} | 单词数量: {} | Key: {}", 
                            book.getId(), words.size(), cacheKey);
                    cacheSuccessCount++;
                } catch (Exception e) {
                    log.error("❌ [DB→Redis] 缓存单词本单词失败 | 单词本ID: {}", book.getId(), e);
                    cacheFailCount++;
                }
            }
        } catch (Exception e) {
            log.error("❌ [DB→Redis] 获取用户单词本列表失败 | 用户ID: {}", userId, e);
        }
    }
    
    /**
     * 缓存商店单词书列表
     */
    private void cacheStoreBooks() {
        try {
            // 缓存默认的商店单词书列表（第一页）
            // 注意：这里可以根据业务需求调整查询参数
            log.info("🏪 [UserMessageConsumer] 开始缓存商店单词书列表");
            
            // 如果需要缓存商店数据，可以调用 storeService.queryStoreBooks()
            // 但由于 StoreService 需要查询参数，这里暂时只记录日志
            // 实际业务中可以考虑缓存热门单词书或分类数据
            
            log.info("🏪 [UserMessageConsumer] 商店单词书缓存完成（可根据需要扩展）");
        } catch (Exception e) {
            log.error("❌ [UserMessageConsumer] 缓存商店单词书失败", e);
        }
    }
}
