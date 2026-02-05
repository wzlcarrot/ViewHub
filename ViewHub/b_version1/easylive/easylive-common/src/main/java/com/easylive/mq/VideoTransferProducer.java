package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 视频转码任务消息生产者
 * 
 * 职责：将视频转码任务发送到 RabbitMQ 消息队列
 * 
 * 使用场景：
 * 1. 当用户上传视频后，异步发送转码任务
 * 2. 避免同步转码影响接口响应时间
 * 3. 支持高并发视频上传场景
 * 4. 消息持久化，防止转码任务丢失
 * 5. 使用 CorrelationData 追踪消息，支持生产者确认机制
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoTransferProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送视频转码任务到消息队列
     * 
     * @param videoInfoFile 视频文件信息
     */
    public void sendTransferTask(VideoInfoFilePost videoInfoFile) {
        try {
            // 将对象转换为 JSON 字符串
            String messageBody = JsonUtils.convertObj2Json(videoInfoFile);

            // 使用 CorrelationData 追踪消息（用于生产者确认回调）
            // messageId 格式：TRANSFER_{uploadId}_{fileId}，便于在回调中识别转码任务
            String messageId = "TRANSFER_" + videoInfoFile.getUploadId() + "_" + videoInfoFile.getFileId();
            CorrelationData correlationData = new CorrelationData(messageId);

            // 发送消息到 RabbitMQ（Direct 交换机）
            // 消息会自动持久化（队列和消息都设置了持久化）
            rabbitTemplate.convertAndSend(
                Constants.RABBITMQ_EXCHANGE_VIDEO_TRANSFER,
                Constants.RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER,
                messageBody,
                correlationData  // 传入 CorrelationData 用于追踪
            );

            log.info("视频转码任务已发送到MQ, uploadId={}, videoId={}, fileId={}, messageId={}", 
                videoInfoFile.getUploadId(), 
                videoInfoFile.getVideoId(), 
                videoInfoFile.getFileId(),
                messageId);
        } catch (Exception e) {
            log.error("发送转码任务到MQ失败, uploadId={}, videoId={}", 
                videoInfoFile.getUploadId(), videoInfoFile.getVideoId(), e);
            throw new RuntimeException("发送转码任务失败", e);
        }
    }

    /**
     * 批量发送视频转码任务到消息队列
     * 
     * @param fileList 视频文件列表
     */
    public void sendTransferTasks(List<VideoInfoFilePost> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return;
        }
        
        for (VideoInfoFilePost file : fileList) {
            sendTransferTask(file);
        }
        
        log.info("批量发送转码任务完成，共 {} 个任务", fileList.size());
    }
}

