package com.easylive.admin.controller;

import com.easylive.entity.po.VideoComment;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.query.VideoDanmuQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.VideoCommentService;
import com.easylive.service.VideoDanmuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/interact")
@RequiredArgsConstructor
public class InteractionController extends ABaseController{

    private final VideoCommentService videoCommentService;

    private final VideoDanmuService videoDanmuService;

    @RequestMapping("/loadComment")
    public ResponseVO loadComment(Integer pageNo, String videoNameFuzzy){
        VideoCommentQuery commentQuery = new VideoCommentQuery();
        commentQuery.setOrderBy("comment_id desc");
        commentQuery.setPageNo(pageNo);
        commentQuery.setQueryVideoInfo(true);
        commentQuery.setVideoNameFuzzy(videoNameFuzzy);

        PaginationResultVO resultVO = videoCommentService.findListByPage(commentQuery);

        return getSuccessResponseVO(resultVO);
    }

    @RequestMapping("/delComment")
    public ResponseVO deleteComment(@NotNull Integer commentId){
        videoCommentService.deleteComment(commentId, null);

        return getSuccessResponseVO(null);
    }


    @RequestMapping("/loadDanmu")
    public ResponseVO loadDanmu(Integer pageNo, String videoNameFuzzy){
        VideoDanmuQuery danmuQuery = new VideoDanmuQuery();
        danmuQuery.setOrderBy("danmu_id desc");
        danmuQuery.setPageNo(pageNo);
        danmuQuery.setQueryVideoInfo(true);
        danmuQuery.setVideoNameFuzzy(videoNameFuzzy);
        PaginationResultVO resultVO = videoDanmuService.findListByPage(danmuQuery);

        return getSuccessResponseVO(resultVO);
    }

    //删除弹幕
    @RequestMapping("/delDanmu")
    public ResponseVO deleteDanmu(@NotNull Integer danmuId){
        videoDanmuService.deleteDanmu(null, danmuId);

        return getSuccessResponseVO(null);
    }


}
