package com.easylive.web.controller;


import com.easylive.annotation.RecordUserMessage;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.UserAction;
import com.easylive.entity.po.VideoComment;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.UserActionQuery;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.entity.vo.VideoCommentResultVO;
import com.easylive.service.UserActionService;
import com.easylive.service.VideoCommentService;
import com.easylive.service.impl.VideoInfoServiceImpl;
import com.easylive.web.annotation.GlobalInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;

@RestController
@Validated
@RequestMapping("/comment")
@Slf4j
@RequiredArgsConstructor
public class VideoCommentController extends ABaseController{

    private final VideoCommentService videoCommentService;

    private final UserActionService userActionService;

    private final VideoInfoServiceImpl videoInfoService;



    @RequestMapping("/postComment")
    @GlobalInterceptor(checkLogin = true)
    @RecordUserMessage(messageType= MessageTypeEnum.COMMENT)
    public ResponseVO postComment(@NotEmpty String videoId,
                                  Integer replyCommentId,  //看看是回复哪条具体评论id
                                  @NotEmpty @Size(max = 500) String content,
                                  @Size(max = 50) String imgPath) {

        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        VideoComment comment = new VideoComment();
        comment.setUserId(tokenUserInfoDto.getUserId());
        comment.setAvatar(tokenUserInfoDto.getAvatar());
        comment.setNickName(tokenUserInfoDto.getNickname());
        comment.setVideoId(videoId);
        comment.setContent(content);
        comment.setImgPath(imgPath);

        videoCommentService.postComment(comment, replyCommentId);
        return getSuccessResponseVO(comment);
    }



    @RequestMapping("/loadComment")
    //orderType 0:按点赞数排序 1:按时间排序
    public ResponseVO loadComment(@NotEmpty String videoId, Integer pageNo, Integer orderType) {

        VideoInfo videoInfo = videoInfoService.getVideoInfoByVideoId(videoId);
        //评论已关闭
        if (videoInfo.getInteraction() != null && videoInfo.getInteraction().contains(Constants.ONE.toString())) {
            return getSuccessResponseVO(new ArrayList<>());
        }

        VideoCommentQuery commentQuery = new VideoCommentQuery();
        commentQuery.setVideoId(videoId);
        commentQuery.setLoadChildren(true);
        commentQuery.setPageNo(pageNo);
        commentQuery.setPageSize(PageSize.SIZE15.getSize());
        commentQuery.setpCommentId(0);
        commentQuery.setLoadChildren(true);
        //默认按点赞数排序
        String orderBy = orderType == null || orderType == 0 ? "top_type desc, like_count desc,comment_id desc" : "top_type desc, comment_id desc";
        commentQuery.setOrderBy(orderBy);

        //通过分页查询，，，拿到每页的评论数据
        PaginationResultVO<VideoComment> commentData = videoCommentService.findListByPage(commentQuery);

        VideoCommentResultVO resultVO = new VideoCommentResultVO();
        resultVO.setCommentData(commentData);

        List<UserAction> userActionList = new ArrayList<>();
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        if (tokenUserInfoDto != null) {
            UserActionQuery userActionQuery = new UserActionQuery();
            userActionQuery.setUserId(tokenUserInfoDto.getUserId());
            userActionQuery.setVideoId(videoId);
            userActionQuery.setActionTypeArray(new Integer[]{UserActionTypeEnum.COMMENT_LIKE.getType(), UserActionTypeEnum.COMMENT_HATE.getType()});
            userActionList = userActionService.findListByParam(userActionQuery);
        }
        resultVO.setUserActionList(userActionList);
        return getSuccessResponseVO(resultVO);
    }

    //置顶评论
    @RequestMapping("/topComment")
    public ResponseVO topComment(@NotNull Integer commentId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        videoCommentService.topComment(commentId, tokenUserInfoDto.getUserId());



        return getSuccessResponseVO(null);
    }

    //置顶评论
    @RequestMapping("/cancelTopComment")
    public ResponseVO cancelTopComment(@NotNull Integer commentId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        videoCommentService.cancelTopComment(commentId, tokenUserInfoDto.getUserId());


        return getSuccessResponseVO(null);
    }


    @RequestMapping("/userDelComment")
    public ResponseVO userDelComment(@NotNull Integer commentId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        VideoComment comment = new VideoComment();
        videoCommentService.deleteComment(commentId, tokenUserInfoDto.getUserId());
        return getSuccessResponseVO(comment);
    }


}

