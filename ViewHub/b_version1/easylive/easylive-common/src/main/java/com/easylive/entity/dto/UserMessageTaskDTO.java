package com.easylive.entity.dto;

import com.easylive.entity.enums.MessageTypeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户消息任务DTO
 * 用于RabbitMQ消息传递
 */
@Data
public class UserMessageTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 视频ID
     */
    private String videoId;

    /**
     * 发送用户ID（操作发起者）
     */
    private String sendUserId;

    /**
     * 消息类型
     */
    private Integer messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 回复的评论ID（如果是回复评论操作）
     */
    private Integer replyCommentId;
}

