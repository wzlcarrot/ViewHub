package com.easylive.cache;

import com.easylive.entity.dto.CacheEvictMessage;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 缓存失效监听器
 * 订阅Redis Pub/Sub缓存失效频道，接收其他实例的缓存失效通知
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private final ThreeLevelCacheManager threeLevelCacheManager;

    /**
     * 缓存失效频道名称
     */
    private static final String CACHE_INVALIDATE_CHANNEL = "cache:invalidation";

    /**
     * 频道主题
     */
    private ChannelTopic channelTopic;

    /**
     * 订阅缓存失效频道
     */
    @PostConstruct
    public void subscribeToCacheInvalidation() {
        channelTopic = new ChannelTopic(CACHE_INVALIDATE_CHANNEL);
        redisMessageListenerContainer.addMessageListener(this, channelTopic);
        log.info("已订阅缓存失效频道: {}", CACHE_INVALIDATE_CHANNEL);
    }

    /**
     * 接收缓存失效消息
     * 
     * @param message Redis消息
     * @param pattern 订阅模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String messageJson = new String(message.getBody());
            CacheEvictMessage cacheEvictMessage = JsonUtils.convertJson2Obj(messageJson, CacheEvictMessage.class);

            if (cacheEvictMessage != null && cacheEvictMessage.getCacheKey() != null) {
                log.debug("收到缓存失效通知, cacheKey: {}", cacheEvictMessage.getCacheKey());
                // 只删除本地缓存，不删除Redis（Redis已经被发出通知的实例删除了）
                threeLevelCacheManager.evictLocal(cacheEvictMessage.getCacheKey());
            }
        } catch (Exception e) {
            log.error("处理缓存失效消息失败", e);
        }
    }
}
