package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.VideoDanmuTaskDTO;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 弹幕任务消息生产者
 * 
 * 职责：将弹幕任务发送到 RabbitMQ 消息队列
 * 
 * 使用场景：
 * 1. 当用户发送弹幕时，异步发送弹幕任务到MQ
 * 2. 避免同步处理影响接口响应时间
 * 3. 支持高并发弹幕发送场景
 * 4. 使用 CorrelationData 追踪消息，支持生产者确认机制
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoDanmuProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送弹幕任务到消息队列
     * 
     * @param danmuTaskDTO 弹幕任务DTO
     */
    public void sendDanmuTask(VideoDanmuTaskDTO danmuTaskDTO) {
        try {
            // 将对象转换为 JSON 字符串
            String messageBody = JsonUtils.convertObj2Json(danmuTaskDTO);

            // 使用 CorrelationData 追踪消息（用于生产者确认回调）
            // messageId 格式：DANMU_{videoId}_{fileId}_{userId}，便于在回调中识别弹幕任务
            String messageId = "DANMU_" + danmuTaskDTO.getVideoId() + "_" + 
                danmuTaskDTO.getFileId() + "_" + danmuTaskDTO.getUserId();
            CorrelationData correlationData = new CorrelationData(messageId);

            // 发送消息到 RabbitMQ（Direct 交换机）
            rabbitTemplate.convertAndSend(
                Constants.RABBITMQ_EXCHANGE_VIDEO_DANMU,
                Constants.RABBITMQ_ROUTING_KEY_VIDEO_DANMU,
                messageBody,
                correlationData  // 传入 CorrelationData 用于追踪
            );

            log.debug("弹幕任务已发送到MQ, videoId={}, fileId={}, userId={}, messageId={}", 
                danmuTaskDTO.getVideoId(), danmuTaskDTO.getFileId(), danmuTaskDTO.getUserId(), messageId);
        } catch (Exception e) {
            log.error("发送弹幕任务到MQ失败, videoId={}, fileId={}, userId={}", 
                danmuTaskDTO.getVideoId(), danmuTaskDTO.getFileId(), danmuTaskDTO.getUserId(), e);
            throw new RuntimeException("发送消息到MQ失败", e);
        }
    }
}

