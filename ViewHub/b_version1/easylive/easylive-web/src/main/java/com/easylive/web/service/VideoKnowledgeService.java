package com.easylive.web.service;

import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.mappers.VideoInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 视频知识库服务
 * 负责加载和管理所有视频的信息（标题、标签、简介）
 */
@Service
@RequiredArgsConstructor
public class VideoKnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(VideoKnowledgeService.class);

    private final VideoInfoMapper videoInfoMapper;

    // 视频知识库缓存（使用volatile保证可见性，使用CopyOnWriteArrayList保证线程安全）
    private volatile List<VideoKnowledgeItem> knowledgeBase = new CopyOnWriteArrayList<>();

    /**
     * 视频知识项
     */
    public static class VideoKnowledgeItem {
        private String videoId;
        private String videoName;
        private String tags;
        private String introduction;
        private Integer playCount;
        private Integer likeCount;
        private Integer commentCount;
        private String nickName;

        public VideoKnowledgeItem(VideoInfo video) {
            this.videoId = video.getVideoId();
            this.videoName = video.getVideoName();
            this.tags = video.getTags();
            this.introduction = video.getIntroduction();
            this.playCount = video.getPlayCount();
            this.likeCount = video.getLikeCount();
            this.commentCount = video.getCommentCount();
            this.nickName = video.getNickName();
        }

        // Getters
        public String getVideoId() { return videoId; }
        public String getVideoName() { return videoName; }
        public String getTags() { return tags; }
        public String getIntroduction() { return introduction; }
        public Integer getPlayCount() { return playCount; }
        public Integer getLikeCount() { return likeCount; }
        public Integer getCommentCount() { return commentCount; }
        public String getNickName() { return nickName; }

        /**
         * 获取完整的文本描述（用于AI理解）
         */
        public String getFullText() {
            StringBuilder sb = new StringBuilder();
            sb.append("视频标题：").append(videoName).append("\n");
            if (tags != null && !tags.isEmpty()) {
                sb.append("标签：").append(tags).append("\n");
            }
            if (introduction != null && !introduction.isEmpty()) {
                sb.append("简介：").append(introduction).append("\n");
            }
            sb.append("播放量：").append(playCount != null ? playCount : 0).append("\n");
            if (nickName != null) {
                sb.append("作者：").append(nickName).append("\n");
            }
            return sb.toString();
        }

        /**
         * 计算与关键词的相关度
         */
        public int calculateRelevance(String keyword) {
            if (keyword == null || keyword.isEmpty()) {
                return 0;
            }
            
            String lowerKeyword = keyword.toLowerCase();
            int score = 0;
            
            // 标题匹配（权重最高）
            if (videoName != null && videoName.toLowerCase().contains(lowerKeyword)) {
                score += 100;
            }
            
            // 标签匹配
            if (tags != null && tags.toLowerCase().contains(lowerKeyword)) {
                score += 50;
            }
            
            // 简介匹配
            if (introduction != null && introduction.toLowerCase().contains(lowerKeyword)) {
                score += 30;
            }
            
            // 播放量加权
            if (playCount != null && playCount > 0) {
                score += Math.min(playCount / 1000, 20);
            }
            
            return score;
        }
    }

    /**
     * 应用启动时加载视频知识库
     */
    @PostConstruct
    public void initKnowledgeBase() {
        try {
            logger.info("开始加载视频知识库...");
            refreshKnowledgeBase();
            logger.info("视频知识库加载完成，共 {} 个视频", knowledgeBase.size());
        } catch (Exception e) {
            logger.error("加载视频知识库失败", e);
        }
    }

    /**
     * 刷新知识库（线程安全版本）
     * 使用CopyOnWriteArrayList保证并发读取安全
     */
    public void refreshKnowledgeBase() {
        VideoInfoQuery query = new VideoInfoQuery();
        query.setOrderBy("last_play_time desc");
        query.setQueryUserInfo(true); // 查询用户信息
        // 不设置分页参数，获取所有视频
        
        List<VideoInfo> videoList = videoInfoMapper.selectList(query);
        
        // 创建新的CopyOnWriteArrayList，保证线程安全
        List<VideoKnowledgeItem> newKnowledgeBase = new CopyOnWriteArrayList<>(
            videoList.stream()
                .map(VideoKnowledgeItem::new)
                .collect(Collectors.toList())
        );
        
        // 原子性替换（volatile保证可见性）
        knowledgeBase = newKnowledgeBase;
        
        logger.info("知识库已刷新，当前视频数量：{}", knowledgeBase.size());
    }

    /**
     * 获取所有视频知识
     */
    public List<VideoKnowledgeItem> getAllVideos() {
        return new ArrayList<>(knowledgeBase);
    }

    /**
     * 根据关键词搜索相关视频
     */
    public List<VideoKnowledgeItem> searchVideos(String keyword, int limit) {
        if (keyword == null || keyword.isEmpty()) {
            return knowledgeBase.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return knowledgeBase.stream()
                .map(video -> new ScoredVideo(video, video.calculateRelevance(keyword)))
                .filter(sv -> sv.score > 0)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(limit)
                .map(sv -> sv.video)
                .collect(Collectors.toList());
    }

    /**
     * 获取知识库摘要（用于AI上下文）
     */
    public String getKnowledgeBaseSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("视频网站知识库概况：\n");
        summary.append("- 总视频数：").append(knowledgeBase.size()).append("\n");
        
        // 统计分类
        long videoCount = knowledgeBase.stream()
                .filter(v -> v.getVideoName() != null)
                .count();
        summary.append("- 有效视频：").append(videoCount).append("\n");
        
        // 热门视频
        List<VideoKnowledgeItem> topVideos = knowledgeBase.stream()
                .filter(v -> v.getPlayCount() != null)
                .sorted((a, b) -> Integer.compare(
                        b.getPlayCount() != null ? b.getPlayCount() : 0,
                        a.getPlayCount() != null ? a.getPlayCount() : 0
                ))
                .limit(5)
                .collect(Collectors.toList());
        
        if (!topVideos.isEmpty()) {
            summary.append("\n热门视频：\n");
            for (VideoKnowledgeItem video : topVideos) {
                summary.append("  - ").append(video.getVideoName())
                        .append("（播放：").append(video.getPlayCount()).append("）\n");
            }
        }
        
        return summary.toString();
    }

    /**
     * 带分数的视频（用于排序）
     */
    private static class ScoredVideo {
        VideoKnowledgeItem video;
        int score;

        ScoredVideo(VideoKnowledgeItem video, int score) {
            this.video = video;
            this.score = score;
        }
    }
}

