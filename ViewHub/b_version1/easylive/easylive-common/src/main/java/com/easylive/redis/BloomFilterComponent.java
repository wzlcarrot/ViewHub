package com.easylive.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 布隆过滤器组件（防缓存穿透）
 *
 * 设计参考 music 项目实现，保持一致的初始化与使用方式：
 * - 预估容量 + 误判率
 * - mightContain* 判断不存在则直接返回
 * - add* 查询后无论存在与否都写入布隆过滤器，避免重复穿透
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BloomFilterComponent {

    private final RedissonClient redissonClient;

    private RBloomFilter<String> videoBloomFilter;
    private RBloomFilter<String> userBloomFilter;
    private RBloomFilter<String> fileBloomFilter;
    private RBloomFilter<String> commentBloomFilter;
    private RBloomFilter<String> danmuBloomFilter;
    private RBloomFilter<String> categoryBloomFilter;

    @PostConstruct
    public void init() {
        // 视频：预估 100 万，误判率 1%
        videoBloomFilter = redissonClient.getBloomFilter("bloomfilter:video");
        if (!videoBloomFilter.isExists()) {
            videoBloomFilter.tryInit(1_000_000L, 0.01);
            log.info("视频布隆过滤器初始化成功，容量100万，误判率1%");
        }

        // 用户：预估 10 万，误判率 1%
        userBloomFilter = redissonClient.getBloomFilter("bloomfilter:user");
        if (!userBloomFilter.isExists()) {
            userBloomFilter.tryInit(100_000L, 0.01);
            log.info("用户布隆过滤器初始化成功，容量10万，误判率1%");
        }

        // 视频文件：预估 100 万，误判率 1%
        fileBloomFilter = redissonClient.getBloomFilter("bloomfilter:video_file");
        if (!fileBloomFilter.isExists()) {
            fileBloomFilter.tryInit(1_000_000L, 0.01);
            log.info("视频文件布隆过滤器初始化成功，容量100万，误判率1%");
        }

        // 评论：预估 500 万，误判率 1%
        commentBloomFilter = redissonClient.getBloomFilter("bloomfilter:comment");
        if (!commentBloomFilter.isExists()) {
            commentBloomFilter.tryInit(5_000_000L, 0.01);
            log.info("评论布隆过滤器初始化成功，容量500万，误判率1%");
        }

        // 弹幕：预估 1000 万，误判率 1%
        danmuBloomFilter = redissonClient.getBloomFilter("bloomfilter:danmu");
        if (!danmuBloomFilter.isExists()) {
            danmuBloomFilter.tryInit(10_000_000L, 0.01);
            log.info("弹幕布隆过滤器初始化成功，容量1000万，误判率1%");
        }

        // 分类：预估 1 万，误判率 1%
        categoryBloomFilter = redissonClient.getBloomFilter("bloomfilter:category");
        if (!categoryBloomFilter.isExists()) {
            categoryBloomFilter.tryInit(10_000L, 0.01);
            log.info("分类布隆过滤器初始化成功，容量1万，误判率1%");
        }
    }

    /** 是否可能存在视频 */
    public boolean mightContainVideo(String videoId) {
        return videoId != null && videoBloomFilter.contains(videoId);
    }

    /** 加入视频ID（存在/不存在都加入，防止穿透） */
    public void addVideo(String videoId) {
        if (videoId != null) {
            videoBloomFilter.add(videoId);
        }
    }

    /** 是否可能存在用户 */
    public boolean mightContainUser(String userId) {
        return userId != null && userBloomFilter.contains(userId);
    }

    /** 加入用户ID */
    public void addUser(String userId) {
        if (userId != null) {
            userBloomFilter.add(userId);
        }
    }

    /** 是否可能存在视频文件 */
    public boolean mightContainFile(String fileId) {
        return fileId != null && fileBloomFilter.contains(fileId);
    }

    /** 加入视频文件ID */
    public void addFile(String fileId) {
        if (fileId != null) {
            fileBloomFilter.add(fileId);
        }
    }

    /** 是否可能存在评论 */
    public boolean mightContainComment(Integer commentId) {
        return commentId != null && commentBloomFilter.contains(String.valueOf(commentId));
    }

    /** 加入评论ID */
    public void addComment(Integer commentId) {
        if (commentId != null) {
            commentBloomFilter.add(String.valueOf(commentId));
        }
    }

    /** 是否可能存在弹幕 */
    public boolean mightContainDanmu(Integer danmuId) {
        return danmuId != null && danmuBloomFilter.contains(String.valueOf(danmuId));
    }

    /** 加入弹幕ID */
    public void addDanmu(Integer danmuId) {
        if (danmuId != null) {
            danmuBloomFilter.add(String.valueOf(danmuId));
        }
    }

    /** 是否可能存在分类 */
    public boolean mightContainCategory(Integer categoryId) {
        return categoryId != null && categoryBloomFilter.contains(String.valueOf(categoryId));
    }

    /** 加入分类ID */
    public void addCategory(Integer categoryId) {
        if (categoryId != null) {
            categoryBloomFilter.add(String.valueOf(categoryId));
        }
    }
}

