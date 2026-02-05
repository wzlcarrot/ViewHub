package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.VideoPlayInfoDto;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 视频播放统计任务消息生产者
 * 
 * 职责：将视频播放统计任务发送到 RabbitMQ 消息队列
 * 
 * 使用场景：
 * 1. 当用户播放视频时，异步发送播放统计任务
 * 2. 避免同步处理影响接口响应时间
 * 3. 支持高并发播放统计场景
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoPlayProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送视频播放统计任务到消息队列
     * 
     * @param videoPlayInfoDto 视频播放信息DTO
     */
    public void sendVideoPlayTask(VideoPlayInfoDto videoPlayInfoDto) {
        try {
            // 将对象转换为 JSON 字符串
            String messageBody = JsonUtils.convertObj2Json(videoPlayInfoDto);

            // 发送消息到 RabbitMQ（Direct 交换机）
            rabbitTemplate.convertAndSend(
                Constants.RABBITMQ_EXCHANGE_VIDEO_PLAY,
                Constants.RABBITMQ_ROUTING_KEY_VIDEO_PLAY,
                messageBody
            );

            log.debug("视频播放统计任务已发送到MQ, videoId={}, userId={}, fileIndex={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId(), videoPlayInfoDto.getFileIndex());
        } catch (Exception e) {
            log.error("发送视频播放统计任务到MQ失败, videoId={}, userId={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId(), e);
            throw new RuntimeException("发送消息到MQ失败", e);
        }
    }
}

