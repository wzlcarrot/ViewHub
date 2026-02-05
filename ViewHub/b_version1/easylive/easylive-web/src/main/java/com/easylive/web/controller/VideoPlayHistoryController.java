package com.easylive.web.controller;


import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.po.VideoPlayHistory;
import com.easylive.entity.query.VideoPlayHistoryQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.VideoPlayHistoryService;
import com.easylive.web.annotation.GlobalInterceptor;
import jdk.nashorn.internal.parser.Token;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

@RestController
@Slf4j
@RequestMapping("/history")
@RequiredArgsConstructor
public class VideoPlayHistoryController extends ABaseController{

    private final VideoPlayHistoryService videoPlayHistoryService;

    @RequestMapping("/loadHistory")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO loadHistory(Integer pageNo){

        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        VideoPlayHistoryQuery query = new VideoPlayHistoryQuery();
        query.setUserId(tokenUserInfoDto.getUserId());
        query.setOrderBy("last_update_time desc");
        query.setPageNo(pageNo);

        query.setQueryVideoDetail( true);   //是否存放视频的详情信息。。通过这个配置来关联videoInfo表

        PaginationResultVO resultVO = videoPlayHistoryService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/cleanHistory")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO cleanHistory(){
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

        VideoPlayHistoryQuery query = new VideoPlayHistoryQuery();
        query.setUserId(tokenUserInfoDto.getUserId());

        videoPlayHistoryService.deleteByParam( query);

        return getSuccessResponseVO(null);

    }



    @RequestMapping("/delHistory")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO delHistory(@NotNull String videoId){
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

        videoPlayHistoryService.deleteVideoPlayHistoryByUserIdAndVideoId(tokenUserInfoDto.getUserId(), videoId);
        return getSuccessResponseVO(null);

    }

}
