package com.easylive.web.controller;


import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.query.VideoDanmuQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.service.*;
import com.easylive.web.annotation.GlobalInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@Validated
@RequestMapping("/ucenter")
@Slf4j
@RequiredArgsConstructor
public class UCenterInteractionController extends ABaseController{
    private final VideoInfoPostService videoInfoPostService;

    private final VideoInfoFilePostService videoInfoFilePostService;

    private final VideoInfoService videoInfoService;

    private final VideoCommentService videoCommentService;

    private final VideoDanmuService videoDanmuService;

    private final StatisticsInfoService statisticsInfoService;

    //评论管理下的视频列表
    @RequestMapping("/loadAllVideo")
    public ResponseVO loadAllVideo(){
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setUserId(tokenUserInfoDto.getUserId());
        infoQuery.setOrderBy("create_time desc");

        List<VideoInfo> videoInfoList = videoInfoService.findListByParam(infoQuery);

        return getSuccessResponseVO(videoInfoList);
    }

    //加载特定视频下的所有评论
    @RequestMapping("/loadComment")
    public ResponseVO loadComment(Integer pageNo, String videoId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        VideoCommentQuery commentQuery = new VideoCommentQuery();
        commentQuery.setVideoUserId(tokenUserInfoDto.getUserId());
        commentQuery.setVideoId(videoId);
        commentQuery.setOrderBy("comment_id desc");
        commentQuery.setPageNo(pageNo);
        commentQuery.setQueryVideoInfo(true);   //设置是否查找视频信息
        PaginationResultVO resultVO = videoCommentService.findListByPage(commentQuery);
        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/delComment")
    public ResponseVO delComment(@NotNull Integer commentId) {
            videoCommentService.deleteComment(commentId, getTokenUserInfoDto().getUserId());
            return getSuccessResponseVO(null);
    }


    @RequestMapping("/loadDanmu")
    public ResponseVO loadDamu(Integer pageNo, String videoId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

        VideoDanmuQuery danmuQuery = new VideoDanmuQuery();
        danmuQuery.setVideoUserId(tokenUserInfoDto.getUserId());    //导入视频的用户信息
        danmuQuery.setVideoId(videoId);
        danmuQuery.setOrderBy("danmu_id desc");
        danmuQuery.setPageNo(pageNo);
        danmuQuery.setQueryVideoInfo(true);  //判断是否要查询视频信息，也就是是否关联videoInfo表
        System.out.println("danmuQuery:"+danmuQuery);
        PaginationResultVO resultVO = videoDanmuService.findListByPage(danmuQuery);
        return getSuccessResponseVO(resultVO);
    }

    @RequestMapping("/delDanmu")
    public ResponseVO delDanmu(@NotNull Integer danmuId){
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        if(tokenUserInfoDto==null){
            return getBusinessErrorResponseVO(new BusinessException(ResponseCodeEnum.CODE_901), null);
        }
        videoDanmuService.deleteDanmu(getTokenUserInfoDto().getUserId(), danmuId);
        return getSuccessResponseVO(null);
    }

}


