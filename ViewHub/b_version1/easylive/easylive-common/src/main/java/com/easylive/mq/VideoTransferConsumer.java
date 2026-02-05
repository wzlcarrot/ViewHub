package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.service.VideoInfoPostService;
import com.easylive.utils.JsonUtils;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 视频转码任务消息消费者
 * 
 * 职责：监听 RabbitMQ 消息，处理视频转码任务
 * 
 * 工作流程：
 * 1. 监听 MQ 中的视频转码任务消息
 * 2. 解析消息内容
 * 3. 调用转码服务执行转码
 * 4. 手动确认消息（ACK）
 * 5. 处理失败消息（NACK，进入死信队列）
 * 
 * 死信队列机制：
 * - 消息处理失败时，拒绝消息（NACK），消息会进入死信队列
 * - 消息超过30分钟未消费，自动进入死信队列
 * - 死信队列中的消息可以用于告警、重试或人工处理
 * 
 * @RabbitListener 注解说明：
 * - queues: 监听的队列名称
 * - concurrency: 并发消费线程数（2-5个，根据服务器性能调整）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoTransferConsumer {

    private final VideoInfoPostService videoInfoPostService;

    /**
     * 处理转码任务消息
     * 
     * @param message JSON 格式的 VideoInfoFilePost 字符串
     * @param channel RabbitMQ 通道（用于手动确认）
     * @param msg RabbitMQ 消息对象（用于获取 deliveryTag）
     */
    @RabbitListener(queues = Constants.RABBITMQ_QUEUE_VIDEO_TRANSFER, 
                    concurrency = "2-5")  // 最小2个，最大5个消费者
    public void onMessage(String message, Channel channel, Message msg) {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        
        try {
            // 将 JSON 字符串转换为对象
            VideoInfoFilePost videoInfoFile = JsonUtils.convertJson2Obj(message, VideoInfoFilePost.class);
            
            if (videoInfoFile == null) {
                log.error("转码任务消息解析失败，message={}", message);
                // 拒绝消息，不重新入队（避免无限循环）
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            log.info("收到转码任务, uploadId={}, videoId={}, fileId={}", 
                videoInfoFile.getUploadId(), videoInfoFile.getVideoId(), videoInfoFile.getFileId());

            // 执行转码（这里会调用 FFmpeg 进行视频转码）
            videoInfoPostService.transferVideoFile(videoInfoFile);

            // 转码成功，手动确认消息
            channel.basicAck(deliveryTag, false);
            
            log.info("转码任务处理成功, uploadId={}, videoId={}", 
                videoInfoFile.getUploadId(), videoInfoFile.getVideoId());

        } catch (Exception e) {
            log.error("转码任务处理失败, message={}", message, e);
            
            try {
                // 拒绝消息，重新入队（true表示重新入队，false表示不重新入队）
                // 设置为false，让消息进入死信队列，避免无限重试
                channel.basicNack(deliveryTag, false, false);
                
                log.warn("转码任务已拒绝，将进入死信队列, message={}", message);
            } catch (IOException ioException) {
                log.error("拒绝消息失败", ioException);
            }
        }
    }

    /**
     * 处理死信队列中的消息
     * 用于接收转码失败或超时的消息
     * 
     * @param message 死信消息
     * @param channel RabbitMQ 通道
     * @param msg RabbitMQ 消息对象
     */
    @RabbitListener(queues = Constants.RABBITMQ_QUEUE_VIDEO_TRANSFER_DLX)
    public void onDeadLetterMessage(String message, Channel channel, Message msg) {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        
        try {
            VideoInfoFilePost videoInfoFile = JsonUtils.convertJson2Obj(message, VideoInfoFilePost.class);
            
            log.error("收到死信队列消息（转码失败或超时）, uploadId={}, videoId={}, fileId={}", 
                videoInfoFile != null ? videoInfoFile.getUploadId() : "unknown",
                videoInfoFile != null ? videoInfoFile.getVideoId() : "unknown",
                videoInfoFile != null ? videoInfoFile.getFileId() : "unknown");
            
            // 这里可以：
            // 1. 发送告警通知（邮件、短信、钉钉等）
            // 2. 记录失败日志到数据库
            // 3. 更新视频状态为转码失败
            // 4. 通知管理员处理
            
            // 示例：更新视频状态为转码失败（需要在Service中实现）
            if (videoInfoFile != null) {
                // videoInfoPostService.markTransferFailed(videoInfoFile);
                log.warn("转码任务失败，建议人工处理, uploadId={}, videoId={}", 
                    videoInfoFile.getUploadId(), videoInfoFile.getVideoId());
            }
            
            // 确认死信消息（避免重复处理）
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("处理死信消息失败, message={}", message, e);
            try {
                // 如果处理死信消息也失败，拒绝消息（避免无限循环）
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("拒绝死信消息失败", ioException);
            }
        }
    }
}

