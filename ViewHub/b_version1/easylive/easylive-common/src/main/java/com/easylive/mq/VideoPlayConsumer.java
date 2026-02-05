package com.easylive.mq;

import com.easylive.component.EsSearchComponent;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.VideoPlayInfoDto;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.service.VideoInfoService;
import com.easylive.service.VideoPlayHistoryService;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 视频播放统计任务消息消费者
 * 
 * 职责：监听 RabbitMQ 消息，处理视频播放统计任务
 * 
 * 工作流程：
 * 1. 监听 MQ 中的视频播放统计任务消息
 * 2. 解析消息内容
 * 3. 增加视频播放次数
 * 4. 保存播放历史记录（如果用户已登录）
 * 5. 记录每日播放统计
 * 6. 更新ES播放数量
 * 
 * @RabbitListener 注解说明：
 * - queues: 监听的队列名称
 * - concurrency: 并发消费线程数（可选）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoPlayConsumer {

    private final VideoInfoService videoInfoService;
    private final VideoPlayHistoryService videoPlayHistoryService;
    private final RedisComponent redisComponent;
    private final EsSearchComponent esSearchComponent;

    /**
     * 处理接收到的消息
     *
     * @param message JSON 格式的 VideoPlayInfoDto 字符串
     */
    @RabbitListener(queues = Constants.RABBITMQ_QUEUE_VIDEO_PLAY)
    public void onMessage(String message) {
        try {
            // 将 JSON 字符串转换为对象
            VideoPlayInfoDto videoPlayInfoDto = JsonUtils.convertJson2Obj(message, VideoPlayInfoDto.class);
            
            if (videoPlayInfoDto == null) {
                log.error("消息解析失败，message={}", message);
                return;
            }

            log.debug("收到视频播放统计任务消息, videoId={}, userId={}, fileIndex={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId(), videoPlayInfoDto.getFileIndex());

            // 增加视频播放次数
            videoInfoService.addReadCount(videoPlayInfoDto.getVideoId());

            // 如果userId不为空，则添加历史记录
            if (videoPlayInfoDto.getUserId() != null) {
                // 记录历史记录
                videoPlayHistoryService.saveHistory(
                    videoPlayInfoDto.getUserId(), 
                    videoPlayInfoDto.getVideoId(), 
                    videoPlayInfoDto.getFileIndex()
                );
            }

            // 按天来记录视频播放
            redisComponent.recordVideoPlayCount(videoPlayInfoDto.getVideoId());

            // 更新ES播放数量 playCount+1
            esSearchComponent.updateDocCount(
                videoPlayInfoDto.getVideoId(), 
                SearchOrderTypeEnum.VIDEO_PLAY.getField(), 
                1
            );

            log.debug("视频播放统计任务处理成功, videoId={}, userId={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId());

        } catch (Exception e) {
            log.error("消费视频播放统计任务消息失败, message={}", message, e);
        }
    }
}

