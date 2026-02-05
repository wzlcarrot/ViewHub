package com.easylive.web.task;

import lombok.RequiredArgsConstructor;
import com.easylive.service.StatisticsInfoService;
import com.easylive.service.VideoInfoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SysTask {

    private final StatisticsInfoService statisticsInfoService;

    private final VideoInfoService videoInfoService;

    @Scheduled(cron = "0 0 0 * * ?")     //这个定时器的主要任务是
    public void statisticData(){
        statisticsInfoService.statisticsData();
    }

    /**
     * 点赞 / 收藏 异步写 + 写聚合刷盘任务
     * 使用 Redis Hash 聚合增量，这里通过定时任务批量刷新 MySQL / ES。
     * 频率可以根据业务压力调整，这里先设置为每 5 秒一次。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void flushLikeCollectAgg() {
        videoInfoService.flushLikeCollectAgg();
    }
}
