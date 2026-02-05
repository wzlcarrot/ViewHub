package com.easylive.redis;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component("redisUtils")
@RequiredArgsConstructor
/*用于封装与 Redis 数据库交互的各种常用操作。它提供了多种方法来操作 Redis 中的不同数据结构，
例如字符串（String）、列表（List）、集合（Set）、有序集合（ZSet）等。*/
public class RedisUtils<V> {

    private final RedisTemplate<String, V> redisTemplate;

    private static final Logger logger = (Logger) LoggerFactory.getLogger(RedisUtils.class);

    /**
     * 随机数生成器（用于缓存过期时间随机化，防止缓存雪崩）
     */
    private static final Random RANDOM = new Random();

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    public void delete(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(key));
            }
        }
    }

    public V get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, V value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            logger.error("设置redisKey:{},value:{}失败", key, value);
            return false;
        }
    }

    public boolean keyExists(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 如果key不存在则设置（原子操作，高并发优化）
     * 
     * @param key 键
     * @param value 值
     * @param time 过期时间（毫秒）
     * @return true表示key不存在且设置成功，false表示key已存在
     */
    public boolean setIfAbsent(String key, V value, long time) {
        try {
            if (time > 0) {
                return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, time, TimeUnit.MILLISECONDS));
            } else {
                return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
            }
        } catch (Exception e) {
            logger.error("setIfAbsent失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     * 
     * 过期时间随机化：基础时间 ± 20%，防止缓存雪崩
     * 例如：1800000毫秒（30分钟）→ 实际过期时间在 1440000-2160000毫秒 之间随机
     *
     * @param key   键
     * @param value 值
     * @param time  基础时间(毫秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean setex(String key, V value, long time) {
        try {
            if (time > 0) {
                // 过期时间随机化：基础时间 ± 20%，防止大量缓存同时过期导致缓存雪崩
                long randomRange = (long) (time * 0.2);
                long offset = RANDOM.nextInt((int) (randomRange * 2)) - randomRange; // [-range, +range]
                long finalExpire = Math.max(time + offset, time / 2); // 确保不会太短
                
                redisTemplate.opsForValue().set(key, value, finalExpire, TimeUnit.MILLISECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            logger.error("设置redisKey:{},value:{}失败", key, value);
            return false;
        }
    }

    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.MILLISECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public List<V> getQueueList(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }


    public boolean push(String key, V value, Long time) {
        try {
            redisTemplate.opsForList().leftPush(key, value);
            if (time != null && time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public long remove(String key, Object value) {
        try {
            Long remove = redisTemplate.opsForList().remove(key, 1, value);
            return remove;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean lpushAll(String key, List<V> values, long time) {
        try {
            redisTemplate.opsForList().leftPushAll(key, values);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public V rpop(String key) {
        try {
            return redisTemplate.opsForList().rightPop(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key, 1);
        return count;
    }

    public Long incrementex(String key, long milliseconds) {
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == 1) {
            //设置过期时间1天
            expire(key, milliseconds);
        }
        return count;
    }

    public Long decrement(String key) {
        Long count = redisTemplate.opsForValue().increment(key, -1);
        if (count <= 0) {
            redisTemplate.delete(key);
        }
        logger.info("key:{},减少数量{}", key, count);
        return count;
    }


    public Set<String> getByKeyPrefix(String keyPrifix) {
        Set<String> keyList = redisTemplate.keys(keyPrifix + "*");
        return keyList;
    }


    public Map<String, V> getBatch(String keyPrifix) {
        Set<String> keySet = redisTemplate.keys(keyPrifix + "*");
        List<String> keyList = new ArrayList<>(keySet);
        List<V> keyValueList = redisTemplate.opsForValue().multiGet(keyList);
        Map<String, V> resultMap = keyList.stream().collect(Collectors.toMap(key -> key, value -> keyValueList.get(keyList.indexOf(value))));
        return resultMap;
    }

    public void zaddCount(String key, V v) {
        redisTemplate.opsForZSet().incrementScore(key, v, 1);
    }


    public List<V> getZSetList(String key, Integer count) {
        Set<V> topElements = redisTemplate.opsForZSet().reverseRange(key, 0, count);
        List<V> list = new ArrayList<>(topElements);
        return list;
    }
    //我感觉就是队列里面加上了过期时间
    public boolean lpush(String key, V value, Long time) {
        try {
            redisTemplate.opsForList().leftPush(key, value);
            if (time != null && time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Set 添加元素（用于幂等与判重）
     *
     * @param key   Set key
     * @param value 元素值
     * @param time  过期时间（毫秒），可为 null
     * @return true: 第一次添加（之前不存在）；false: 已存在（幂等/重复请求）
     */
    public boolean sAdd(String key, V value, Long time) {
        try {
            Long added = redisTemplate.opsForSet().add(key, value);
            if (time != null && time > 0) {
                expire(key, time);
            }
            return added != null && added > 0;
        } catch (Exception e) {
            logger.error("sAdd失败, key={}, value={}", key, value, e);
            return false;
        }
    }

    /**
     * Hash 自增（用于写聚合）
     *
     * @param key   hash key
     * @param field 字段
     * @param delta 增量（可为负）
     */
    public void hIncrBy(String key, String field, long delta) {
        try {
            redisTemplate.<String, Object>opsForHash().increment(key, field, delta);
        } catch (Exception e) {
            logger.error("hIncrBy失败, key={}, field={}, delta={}", key, field, delta, e);
        }
    }

    /**
     * 获取 Hash 单个字段的值
     *
     * @param key   hash key
     * @param field 字段
     * @return 字段值，如果不存在返回null
     */
    public Long hGet(String key, String field) {
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            if (value == null) {
                return null;
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                logger.error("hGet值转换失败, key={}, field={}, value={}", key, field, value, e);
                return null;
            }
        } catch (Exception e) {
            logger.error("hGet失败, key={}, field={}", key, field, e);
            return null;
        }
    }

    /**
     * 获取 Hash 全量数据并删除（快照语义，适合定时任务聚合）
     */
    public Map<String, Long> hGetAllAndDelete(String key) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                return new HashMap<>();
            }
            redisTemplate.delete(key);
            Map<String, Long> result = new HashMap<>(entries.size());
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String field = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                long val = 0L;
                if (value != null) {
                    try {
                        val = Long.parseLong(String.valueOf(value));
                    } catch (NumberFormatException ignore) {
                    }
                }
                result.put(field, val);
            }
            return result;
        } catch (Exception e) {
            logger.error("hGetAllAndDelete失败, key={}", key, e);
            return new HashMap<>();
        }
    }

    /**
     * Set 判重：检查成员是否存在
     */
    public boolean sIsMember(String key, V value) {
        try {
            Boolean result = redisTemplate.opsForSet().isMember(key, value);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.error("sIsMember失败, key={}, value={}", key, value, e);
            return false;
        }
    }

    /**
     * Set 删除成员
     */
    public long sRem(String key, V value) {
        try {
            Long removed = redisTemplate.opsForSet().remove(key, value);
            return removed == null ? 0L : removed;
        } catch (Exception e) {
            logger.error("sRem失败, key={}, value={}", key, value, e);
            return 0L;
        }
    }

    /**
     * Set 大小
     */
    public long sCard(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            logger.error("sCard失败, key={}", key, e);
            return 0L;
        }
    }
}
