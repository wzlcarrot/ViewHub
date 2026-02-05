package com.easylive.admin.controller;


import com.easylive.annotation.RecordUserMessage;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.entity.po.VideoInfoPost;
import com.easylive.entity.query.VideoInfoFilePostQuery;
import com.easylive.entity.query.VideoInfoPostQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.service.VideoInfoFilePostService;
import com.easylive.service.VideoInfoPostService;
import com.easylive.service.VideoInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@Validated
@RequestMapping("/videoInfo")
@RequiredArgsConstructor
public class VideoInfoController extends ABaseController{

    private final VideoInfoPostService videoInfoPostService;

    private final VideoInfoFilePostService videoInfoFilePostService;

    private final VideoInfoService videoInfoService;


    @RequestMapping("/loadVideoList")
    public ResponseVO loadVideoList(VideoInfoPostQuery videoInfoPostQuery) {
        videoInfoPostQuery.setOrderBy("v.last_update_time desc");
        videoInfoPostQuery.setQueryCountInfo(true);
        videoInfoPostQuery.setQueryUserInfo(true);
        PaginationResultVO resultVO = videoInfoPostService.findListByPage(videoInfoPostQuery);
        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/auditVideo")
    //主要是系统消息
    public ResponseVO auditVideo(@NotEmpty String videoId, @NotNull Integer status, String reason) {
        videoInfoPostService.auditVideo(videoId, status, reason);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/recommendVideo")
    public ResponseVO recommendVideo(@NotEmpty String videoId) {
        videoInfoService.recommendVideo(videoId);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/deleteVideo")
    public ResponseVO deleteVideo(@NotEmpty String videoId) {
        System.out.println("deleteVideoId:"+videoId);
        videoInfoService.deleteVideo(videoId,null);
        return getSuccessResponseVO(null);
    }


    //为什么写videoInfoPost而不是videoInfo了，，因为有的视频可能没有转码成功
    @RequestMapping("/loadVideoPList")
    public ResponseVO loadVideoPList(String videoId) {
        VideoInfoFilePostQuery videoInfoFilePostQuery = new VideoInfoFilePostQuery();
        videoInfoFilePostQuery.setOrderBy("file_index asc");
        videoInfoFilePostQuery.setVideoId(videoId);
        List<VideoInfoFilePost> resultVO = videoInfoFilePostService.findListByParam(videoInfoFilePostQuery);

        return getSuccessResponseVO(resultVO);
    }
}
