package com.easylive.web.controller;
import com.easylive.component.EsSearchComponent;
import com.easylive.component.RedisComponent;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.*;

import com.easylive.entity.po.UserAction;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.po.VideoInfoFile;

import com.easylive.entity.query.UserActionQuery;
import com.easylive.entity.query.VideoInfoFileQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;

import com.easylive.entity.vo.VideoInfoResultVo;
import com.easylive.exception.BusinessException;

import com.easylive.service.UserActionService;
import com.easylive.service.VideoInfoFileService;
import com.easylive.service.VideoInfoService;
import com.easylive.utils.CopyTools;

import jdk.nashorn.internal.parser.Token;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/video")
@Slf4j
@RequiredArgsConstructor
public class VideoController extends ABaseController {

    private final VideoInfoService videoInfoService;

    private final VideoInfoFileService videoInfoFileService;

    private final RedisComponent redisComponent;

    private final UserActionService userActionService;

    private final EsSearchComponent esSearchComponent;

    private final com.easylive.web.websocket.OnlineCountWebSocketHandler onlineCountWebSocketHandler;

    @RequestMapping("/loadRecommendVideo")
    public ResponseVO loadRecommendVideo() {
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setQueryUserInfo(true);
        videoInfoQuery.setOrderBy("create_time desc");
        videoInfoQuery.setRecommendType(VideoRecommendTypeEnum.RECOMMEND.getType());   //这里设置成推荐类型
        List<VideoInfo> recommendVideoList = videoInfoService.findListByParam(videoInfoQuery);
        log.info("到此一游123");
        return getSuccessResponseVO(recommendVideoList);
    }

    @RequestMapping("/loadVideo")     //pageNo表示的意思是第几页
    public ResponseVO loadVideo(Integer pCategoryId, Integer categoryId, Integer pageNo) {
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setCategoryId(categoryId);
        videoInfoQuery.setpCategoryId(pCategoryId);
        videoInfoQuery.setPageNo(pageNo);
        videoInfoQuery.setQueryUserInfo(true);
        videoInfoQuery.setOrderBy("create_time desc");
        if (categoryId == null && pCategoryId == null) {
            videoInfoQuery.setRecommendType(VideoRecommendTypeEnum.NO_RECOMMEND.getType());
        }
        PaginationResultVO resultVO = videoInfoService.findListByPage(videoInfoQuery);
        return getSuccessResponseVO(resultVO);
    }



    @RequestMapping("/getVideoInfo")
    public ResponseVO getVideoInfo(@NotEmpty String videoId) {
        VideoInfo videoInfo = videoInfoService.getVideoInfoByVideoId(videoId);
        if (null == videoInfo) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        TokenUserInfoDto userInfoDto = getTokenUserInfoDto();

        List<UserAction> userActionList = new ArrayList<>();
        if(userInfoDto!=null){
            UserActionQuery userActionQuery = new UserActionQuery();
            userActionQuery.setUserId(userInfoDto.getUserId());
            userActionQuery.setVideoId(videoId);
            userActionQuery.setActionTypeArray(new Integer[]{UserActionTypeEnum.VIDEO_LIKE.getType(),
                                                            UserActionTypeEnum.VIDEO_COLLECT.getType(),
                                                            UserActionTypeEnum.VIDEO_COIN.getType()
            });

            userActionList = userActionService.findListByParam(userActionQuery);
        }
        VideoInfoResultVo resultVo = new VideoInfoResultVo(videoInfo, userActionList);
        resultVo.setVideoInfo(videoInfo);
        resultVo.setUserActionList(userActionList);
        return getSuccessResponseVO(resultVo);
    }


    @RequestMapping("/loadVideoPList")

    public ResponseVO loadVideoPList(@NotEmpty String videoId) {
        VideoInfoFileQuery videoInfoQuery = new VideoInfoFileQuery();
        videoInfoQuery.setVideoId(videoId);
        videoInfoQuery.setOrderBy("file_index asc");
        List<VideoInfoFile> fileList = videoInfoFileService.findListByParam(videoInfoQuery);
        return getSuccessResponseVO(fileList);
    }

    /**
     * 上报视频在线人数（已优化：WebSocket实时推送）
     * 
     * 注意：如果前端已建立WebSocket连接，此接口主要用于兼容性
     * 实际在线人数变化会通过WebSocket实时推送
     */
    @RequestMapping("/reportVideoPlayOnline")
    public ResponseVO reportVideoPlayOnline(@NotEmpty String fileId, String deviceId) {
        Integer count = redisComponent.reportVideoPlayOnline(fileId, deviceId);
        
        // 触发WebSocket广播（如果已建立连接）
        try {
            onlineCountWebSocketHandler.broadcastOnlineCount(fileId);
        } catch (Exception e) {
            // WebSocket广播失败不影响主流程
            log.debug("WebSocket广播在线人数失败, fileId={}", fileId);
        }
        
        return getSuccessResponseVO(count);
    }


    @RequestMapping("/search")

    public ResponseVO search(@NotEmpty String keyword, String orderType, Integer pageNo) {
        redisComponent.addKeyWordCount(keyword);
        // 处理前端传过来的字符串 "null"，转换为真正的 null
        Integer orderTypeInt = null;
        if (orderType != null && !orderType.trim().isEmpty() && !"null".equalsIgnoreCase(orderType)) {
            try {
                orderTypeInt = Integer.valueOf(orderType);
            } catch (NumberFormatException e) {
                log.warn("orderType参数格式错误: {}", orderType);
                orderTypeInt = null;
            }
        }
        PaginationResultVO resultVO = esSearchComponent.search(true, keyword, orderTypeInt, pageNo, PageSize.SIZE30.getSize());
        return getSuccessResponseVO(resultVO);
    }

    @RequestMapping("/getVideoRecommend")
    public ResponseVO getVideoRecommend(@NotEmpty String keyword, @NotEmpty String videoId) {
        List<VideoInfo> videoInfoList = esSearchComponent.search(false, keyword, SearchOrderTypeEnum.VIDEO_PLAY.getType(), 1, PageSize.SIZE10.getSize()).getList();
        // 使用传统for循环替换stream过滤
        List<VideoInfo> result = new ArrayList<>();
        for (VideoInfo item : videoInfoList) {
            if (!item.getVideoId().equals(videoId)) {
                result.add(item);
            }
        }
        return getSuccessResponseVO(result);
    }

    @RequestMapping("/getSearchKeywordTop")
    public ResponseVO getSearchKeywordTop() {
        List<String> keywordList = redisComponent.getKeyWordList();
        return getSuccessResponseVO(keywordList);
    }

    @RequestMapping("/loadHotVideoList")
    public ResponseVO loadHotVideoList(Integer pageNo) {
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setPageNo(pageNo);
        videoInfoQuery.setQueryUserInfo(true);
        videoInfoQuery.setOrderBy("play_count desc");
        videoInfoQuery.setLastPlayHour(Constants.HOUR_24);

        PaginationResultVO resultVO = videoInfoService.findListByPage(videoInfoQuery);
        System.out.println("result:"+resultVO);
        return getSuccessResponseVO(resultVO);
    }

}


