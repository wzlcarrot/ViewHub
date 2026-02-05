package com.easylive.web.task;

/**
 * 转码队列任务类（已废弃）
 * 
 * 说明：
 * - 原实现使用 Redis List + 轮询方式消费转码任务
 * - 现已迁移到 RabbitMQ + 死信队列实现
 * - 转码任务由 VideoTransferConsumer 自动消费，无需轮询
 * 
 * 如需删除此类，请确保：
 * 1. VideoTransferConsumer 正常工作
 * 2. RabbitMQ 配置正确
 * 3. 转码任务能正常发送和消费
 */

/*
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.service.VideoInfoPostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class ExeCuteQueueTask {

    private ExecutorService executorService = Executors.newFixedThreadPool(Constants.length_2);

    @Resource
    private VideoInfoPostService videoInfoPostService;

    @Resource
    private RedisComponent redisComponent;

    //保留它主要是为了代码的规范性和可读性，明确表示这是一个初始化方法。
    @PostConstruct
    public void consumeTransferFileQueue() {
        executorService.execute(() -> {
            while (true) {
                try {
                    VideoInfoFilePost videoInfoFile = (VideoInfoFilePost) redisComponent.getFileFromTransferQueue();
                    if (videoInfoFile == null) {
                        Thread.sleep(1500);
                        continue;
                    }
                    videoInfoPostService.transferVideoFile(videoInfoFile);
                } catch (Exception e) {
                    log.error("获取转码文件队列信息失败", e);
                }
            }
        });
    }

}
*/
