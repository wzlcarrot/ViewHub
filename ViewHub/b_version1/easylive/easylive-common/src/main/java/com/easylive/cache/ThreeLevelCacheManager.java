package com.easylive.cache;

import com.easylive.entity.dto.CacheEvictMessage;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.JsonUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.easylive.entity.constant.Constants.REDIS_KEY_PREFIX;

/**
 * 三级缓存管理器
 * 实现 Caffeine(L1) -> Redis(L2) -> MySQL(L3) 的三级缓存架构
 * 
 * 缓存策略：
 * 1. 先查询本地缓存(Caffeine)，命中则直接返回
 * 2. 本地缓存未命中，查询Redis，命中则写入本地缓存并返回
 * 3. Redis未命中，使用分布式锁防止缓存击穿，查询MySQL，命中则写入Redis和本地缓存并返回
 * 
 * 防缓存雪崩：
 * - Redis缓存过期时间采用随机化策略（基础时间 ± 20%）
 * - 例如：30分钟 → 实际过期时间在 24-36分钟 之间随机
 * - 防止大量缓存同时过期导致数据库压力激增
 * 
 * 防缓存击穿：
 * - Redis未命中时，使用Redisson分布式锁
 * - 锁等待时间：100ms，锁定时间：3秒
 * - 双重检查：获取锁后再次查询Redis（可能其他线程已经加载了）
 * - 防止热点数据过期时，大量并发请求同时访问数据库
 * 
 * 更新策略：
 * 1. 更新数据时，先更新MySQL
 * 2. 删除Redis中的缓存
 * 3. 删除本地缓存
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ThreeLevelCacheManager {

    private final RedisUtils<Object> redisUtils;

    private final RedissonClient redissonClient;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存失效频道名称
     */
    private static final String CACHE_INVALIDATE_CHANNEL = "cache:invalidation";

    /**
     * 本地缓存实例
     * key: 缓存键
     * value: 缓存值
     */
    private final Cache<String, Object> localCache = Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 分布式锁键前缀
     */
    private static final String LOCK_KEY_PREFIX = "lock:cache:";

    /**
     * Redis缓存过期时间（毫秒），默认30分钟
     */
    private static final long REDIS_EXPIRE_TIME = 30 * 60 * 1000L;

    /**
     * 分布式锁等待时间（毫秒）
     */
    private static final long LOCK_WAIT_TIME = 100L;

    /**
     * 分布式锁持有时间（毫秒）
     */
    private static final long LOCK_LEASE_TIME = 3000L;

    /**
     * Single-flight 等待超时时间（毫秒）
     * 超时后降级走原有 get 逻辑，避免线程长时间堆积
     */
    private static final long SINGLE_FLIGHT_WAIT_TIMEOUT = 2000L;

    /**
     * Single-flight 加载中的 Future 映射
     * key: 缓存键
     * value: 正在加载该 key 的 Future
     */
    private final Map<String, CompletableFuture<Object>> loadingMap = new ConcurrentHashMap<>();

    /**
     * 获取缓存数据（三级缓存查询，带分布式锁防缓存击穿）
     * 
     * 当前实现：直接使用三级缓存 + 分布式锁防击穿
     * Single-Flight锁代码已保留但未启用（见下方注释代码），可作为后续优化
     * 
     * @param key 缓存键
     * @param loader 数据加载器（从MySQL加载数据）
     * @param <T> 返回类型
     * @return 缓存数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Function<String, T> loader) {
        // 当前实现：直接使用三级缓存（分布式锁防击穿）
        return doGetInternal(key, loader);
        
        /* ========== Single-Flight锁实现（已保留但未启用，可作为后续优化） ==========
        // Single-flight 包装：同一 key 的并发请求只触发一次底层加载，其余请求等待结果
        while (true) {
            CompletableFuture<Object> existingFuture = loadingMap.get(key);
            if (existingFuture != null) {
                // 已有线程在加载该 key，当前线程等待结果
                try {
                    Object result = existingFuture.get(SINGLE_FLIGHT_WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
                    return (T) result;
                } catch (TimeoutException e) {
                    log.warn("Single-flight 等待超时，降级走原始三级缓存逻辑，key: {}", key);
                    // 等待超时，降级直接执行原始 get 逻辑，避免请求堆积
                    return doGetInternal(key, loader);
                } catch (ExecutionException e) {
                    log.error("Single-flight 加载发生异常，降级走原始三级缓存逻辑，key: {}", key, e);
                    return doGetInternal(key, loader);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Single-flight 等待结果时被中断，降级走原始三级缓存逻辑，key: {}", key, e);
                    return doGetInternal(key, loader);
                }
            }

            // 尝试成为加载线程
            CompletableFuture<Object> newFuture = new CompletableFuture<>();
            CompletableFuture<Object> previous = (CompletableFuture<Object>) loadingMap.putIfAbsent(key, newFuture);
            if (previous == null) {
                // 当前线程成为加载者
                try {
                    T result = doGetInternal(key, loader);
                    newFuture.complete(result);
                    return result;
                } catch (Throwable ex) {
                    // 将异常传播给等待线程
                    newFuture.completeExceptionally(ex);
                    if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    }
                    throw new RuntimeException(ex);
                } finally {
                    // 确保移除，避免内存泄漏
                    loadingMap.remove(key, newFuture);
                }
            }
            // putIfAbsent 失败，说明已有加载线程，循环回去作为等待者
        }
        ========== Single-Flight锁实现（结束） ========== */
    }

    /**
     * 三级缓存查询逻辑（当前使用）
     * 实现：L1(Caffeine) -> L2(Redis) -> L3(MySQL)
     * 防击穿：使用分布式锁 + 双重检查
     * 降级：Redis异常时降级查询数据库
     */
    @SuppressWarnings("unchecked")
    private <T> T doGetInternal(String key, Function<String, T> loader) {
        // L1: 查询本地缓存
        Object value = localCache.getIfPresent(key);
        if (value != null) {
            log.debug("L1缓存命中，key: {}", key);
            return (T) value;
        }

        // L2: 查询Redis缓存
        String redisKey = REDIS_KEY_PREFIX + key;
        try {
            value = redisUtils.get(redisKey);
            if (value != null) {
                log.debug("L2缓存命中，key: {}", key);
                // 回写本地缓存
                localCache.put(key, value);
                return (T) value;
            }
        } catch (Exception e) {
            log.error("Redis查询异常，降级查询数据库，key: {}", key, e);
            // 降级：直接查询数据库，保证服务可用性
            return loader.apply(key);
        }

        // L3: Redis未命中，使用分布式锁防止缓存击穿
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，等待100ms，锁定3秒
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.MILLISECONDS)) {
                try {
                    // 双重检查：再次查询Redis（可能其他线程已经加载了）
                    try {
                        value = redisUtils.get(redisKey);
                        if (value != null) {
                            log.debug("L2缓存命中（双重检查），key: {}", key);
                            // 回写本地缓存
                            localCache.put(key, value);
                            return (T) value;
                        }
                    } catch (Exception e) {
                        log.error("Redis查询异常（双重检查），降级查询数据库，key: {}", key, e);
                        // 降级：直接查询数据库
                        T dbValue = loader.apply(key);
                        if (dbValue != null) {
                            // 尝试写入本地缓存（Redis不可用，至少保证本地缓存可用）
                            localCache.put(key, dbValue);
                        }
                        return dbValue;
                    }
                    
                    // 查询MySQL数据库
                    log.debug("L3数据库查询，key: {}", key);
                    T dbValue = loader.apply(key);
                    if (dbValue != null) {
                        // 写入Redis缓存（会自动使用随机过期时间）
                        redisUtils.setex(redisKey, dbValue, REDIS_EXPIRE_TIME);
                        // 写入本地缓存
                        localCache.put(key, dbValue);
                        log.debug("数据查询成功并缓存，key: {}", key);
                    }
                    return dbValue;
                } finally {
                    // 释放锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 获取锁失败，等待一小段时间后重试Redis
                log.debug("获取锁失败，等待后重试Redis，key: {}", key);
                try {
                    Thread.sleep(50);
                    value = redisUtils.get(redisKey);
                    if (value != null) {
                        log.debug("L2缓存命中（重试），key: {}", key);
                        // 回写本地缓存
                        localCache.put(key, value);
                        return (T) value;
                    }
                    // 如果重试后仍未命中，直接查询数据库（降级策略）
                    log.warn("重试后Redis仍未命中，直接查询数据库，key: {}", key);
                    T dbValue = loader.apply(key);
                    if (dbValue != null) {
                        // 尝试写入Redis缓存（可能失败，但不影响返回）
                        try {
                            redisUtils.setex(redisKey, dbValue, REDIS_EXPIRE_TIME);
                        } catch (Exception e) {
                            log.warn("写入Redis缓存失败，key: {}", key, e);
                        }
                        // 写入本地缓存
                        localCache.put(key, dbValue);
                    }
                    return dbValue;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待重试时被中断，降级查询数据库，key: {}", key, e);
                    return loader.apply(key);
                } catch (Exception e) {
                    log.error("Redis重试查询异常，降级查询数据库，key: {}", key, e);
                    // 降级：直接查询数据库
                    return loader.apply(key);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁时被中断，key: {}", key, e);
            // 降级：直接查询数据库
            return loader.apply(key);
        } catch (Exception e) {
            log.error("获取锁时发生异常，降级查询数据库，key: {}", key, e);
            // 降级：直接查询数据库
            return loader.apply(key);
        }
    }

    /**
     * 设置缓存数据
     * 
     * @param key 缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        if (value == null) {
            return;
        }
        // 写入本地缓存
        localCache.put(key, value);
        // 写入Redis缓存
        String redisKey = REDIS_KEY_PREFIX + key;
        redisUtils.setex(redisKey, value, REDIS_EXPIRE_TIME);
    }

    /**
     * 删除缓存数据（三级缓存删除 + 广播通知其他实例）
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        // 删除本地缓存
        localCache.invalidate(key);
        // 删除Redis缓存
        String redisKey = REDIS_KEY_PREFIX + key;
        redisUtils.delete(redisKey);
        // 广播缓存失效通知到其他实例
        publishCacheInvalidation(key);
        log.debug("删除三级缓存并广播通知，key: {}", key);
    }

    /**
     * 仅删除本地缓存（不删除Redis，不广播）
     * 用于接收其他实例的缓存失效通知
     *
     * @param key 缓存键
     */
    public void evictLocal(String key) {
        localCache.invalidate(key);
        log.debug("删除本地缓存，key: {}", key);
    }

    /**
     * 发布缓存失效通知到Redis Pub/Sub频道
     * 通知其他实例删除对应的本地缓存
     *
     * @param key 缓存键
     */
    private void publishCacheInvalidation(String key) {
        try {
            CacheEvictMessage message = CacheEvictMessage.of(key);
            // 直接发送对象，让Redis的JSON序列化器自动处理
            redisTemplate.convertAndSend(CACHE_INVALIDATE_CHANNEL, message);
            log.debug("发布缓存失效通知，key: {}", key);
        } catch (Exception e) {
            log.error("发布缓存失效通知失败，key: {}", key, e);
            // 发布失败不影响主流程，其他实例会在缓存过期后自动更新
        }
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        // 清空本地缓存
        localCache.invalidateAll();
        log.info("清空本地缓存");
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 统计信息字符串
     */
    public String getStats() {
        return localCache.stats().toString();
    }
}

