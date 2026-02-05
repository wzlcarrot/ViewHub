package com.easylive.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 缓存失效消息
 * 用于Redis Pub/Sub广播缓存失效通知
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictMessage implements Serializable {

    /**
     * 缓存键
     */
    private String cacheKey;

    /**
     * 缓存类型
     */
    private String cacheType;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 创建缓存失效消息
     * 
     * @param cacheKey 缓存键
     * @return 缓存失效消息
     */
    public static CacheEvictMessage of(String cacheKey) {
        return new CacheEvictMessage(cacheKey, "THREE_LEVEL", System.currentTimeMillis());
    }
}
