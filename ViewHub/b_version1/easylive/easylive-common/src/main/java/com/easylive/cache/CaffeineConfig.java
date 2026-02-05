package com.easylive.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine本地缓存配置类
 * 用于配置本地缓存的基本参数
 */
@Configuration
public class CaffeineConfig {

    /**
     * 创建Caffeine缓存构建器
     * 配置：
     * - 初始容量：100
     * - 最大容量：1000
     * - 写入后过期时间：5分钟
     * - 访问后过期时间：10分钟
     * - 开启统计功能
     */
    @Bean
    public Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats();
    }
}

