package com.easylive.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量块实体
 * 用于存储视频信息的分块向量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorBlock {
    /**
     * 视频ID
     */
    private String videoId;
    
    /**
     * 块类型：title（标题）、tags（标签）、introduction（简介）
     */
    private String blockType;
    
    /**
     * 块内容
     */
    private String blockContent;
    
    /**
     * 向量（384维）
     */
    private float[] contentVector;
    
    /**
     * 块权重（用于检索时加权）
     */
    private Integer blockWeight;
    
    /**
     * 构建标题块
     */
    public static VectorBlock buildTitleBlock(String videoId, String title, float[] vector) {
        return new VectorBlock(videoId, "title", title, vector, 100);
    }
    
    /**
     * 构建标签块
     */
    public static VectorBlock buildTagsBlock(String videoId, String tags, float[] vector) {
        return new VectorBlock(videoId, "tags", tags, vector, 50);
    }
    
    /**
     * 构建简介块
     */
    public static VectorBlock buildIntroductionBlock(String videoId, String introduction, float[] vector) {
        return new VectorBlock(videoId, "introduction", introduction, vector, 30);
    }
}

