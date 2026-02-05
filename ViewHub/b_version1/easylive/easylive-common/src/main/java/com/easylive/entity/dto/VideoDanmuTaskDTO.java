package com.easylive.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Date;

/**
 * 弹幕任务DTO
 * 用于RabbitMQ消息传递
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoDanmuTaskDTO implements Serializable {
    
    /**
     * 视频ID
     */
    private String videoId;
    
    /**
     * 唯一ID（fileId）
     */
    private String fileId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 发布时间
     */
    private Date postTime;
    
    /**
     * 内容
     */
    private String text;
    
    /**
     * 展示位置
     */
    private Integer mode;
    
    /**
     * 颜色
     */
    private String color;
    
    /**
     * 展示时间
     */
    private Integer time;

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getPostTime() {
        return postTime;
    }

    public void setPostTime(Date postTime) {
        this.postTime = postTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getMode() {
        return mode;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

}

