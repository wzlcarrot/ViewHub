package com.easylive.web.controller;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.po.VideoDanmu;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.VideoDanmuQuery;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.VideoDanmuService;
import com.easylive.service.impl.VideoInfoServiceImpl;
import com.easylive.web.websocket.DanmuWebSocketHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Date;

@RestController
@Validated
@RequestMapping("/danmu")
@Slf4j
@RequiredArgsConstructor
public class VideoDanmuController extends ABaseController {

    private final VideoDanmuService videoDanmuService;

    private final VideoInfoServiceImpl videoInfoService;

    private final DanmuWebSocketHandler danmuWebSocketHandler;

    /**
     * 发送弹幕
     *
     * 流程（数据优先策略）：
     * 1. 保存弹幕（RabbitMQ → Redis → MySQL批量写入）✅ 已优化：优先保证数据持久化
     * 2. WebSocket实时推送（通过Redis Pub/Sub广播）✅
     */
    @RequestMapping("/postDanmu")
    public ResponseVO postDanmu(@NotEmpty String videoId,
                                @NotEmpty String fileId,
                                @NotEmpty @Size(max = 200) String text,
                                @NotNull Integer mode,
                                @NotEmpty String color,
                                @NotNull Integer time){

       VideoDanmu videoDanmu = new VideoDanmu();
       videoDanmu.setVideoId(videoId);
       videoDanmu.setFileId(fileId);
       videoDanmu.setText(text);
       videoDanmu.setMode(mode);
       videoDanmu.setColor(color);
       videoDanmu.setTime(time);
       videoDanmu.setUserId(getTokenUserInfoDto().getUserId());
       videoDanmu.setPostTime(new Date());

       // 1. 保存弹幕（RabbitMQ → Redis → MySQL批量写入）
       videoDanmuService.saveVideoDanmu(videoDanmu);

       // 2. WebSocket实时推送（立即推送给所有在线用户）
       try {
           danmuWebSocketHandler.broadcastDanmu(fileId, videoDanmu);
           log.debug("弹幕已通过WebSocket推送, fileId={}, danmuId={}", fileId, videoDanmu.getDanmuId());
       } catch (Exception e) {
           log.error("WebSocket推送弹幕失败, fileId={}", fileId, e);
           // WebSocket推送失败不影响主流程，弹幕已保存到MQ和Redis
       }

       return getSuccessResponseVO(null);
   }


    @RequestMapping("/loadDanmu")
    public ResponseVO loadDanmu(@NotEmpty String fileId, @NotEmpty String videoId) {

        VideoInfo videoInfo = videoInfoService.getVideoInfoByVideoId(videoId);
        if (videoInfo.getInteraction() != null && videoInfo.getInteraction().contains(Constants.ZERO.toString())) {
            return getSuccessResponseVO(new ArrayList<>());
        }

        VideoDanmuQuery videoDanmuQuery = new VideoDanmuQuery();
        videoDanmuQuery.setFileId(fileId);
        videoDanmuQuery.setOrderBy("danmu_id asc");
        return getSuccessResponseVO(videoDanmuService.findListByParam(videoDanmuQuery));
    }

}
