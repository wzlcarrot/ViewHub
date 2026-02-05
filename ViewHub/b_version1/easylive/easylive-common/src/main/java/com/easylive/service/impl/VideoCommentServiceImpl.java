package com.easylive.service.impl;


import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.CommentTopTypeEnum;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.po.VideoComment;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.UserInfoMapper;
import com.easylive.mappers.VideoCommentMapper;
import com.easylive.mappers.VideoInfoMapper;

import com.easylive.redis.BloomFilterComponent;
import lombok.RequiredArgsConstructor;
import com.easylive.service.VideoCommentService;
import com.easylive.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;


/**
 * 评论 业务接口实现
 */
@Service("videoCommentService")
@Slf4j
@RequiredArgsConstructor
public class VideoCommentServiceImpl implements VideoCommentService {

    private final VideoCommentMapper<VideoComment, VideoCommentQuery> videoCommentMapper;

    private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final BloomFilterComponent bloomFilterComponent;



    /**
     * 加载子类评论
     */
    @Override
    public List<VideoComment> findListByParam(VideoCommentQuery param) {
        if (param.getLoadChildren() != null && param.getLoadChildren()) {
            System.out.println("到此一游");
            return this.videoCommentMapper.selectListWithChildren(param);
        }
        return this.videoCommentMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(VideoCommentQuery param) {
        return this.videoCommentMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<VideoComment> findListByPage(VideoCommentQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<VideoComment> list = this.findListByParam(param);
        PaginationResultVO<VideoComment> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(VideoComment bean) {
        return this.videoCommentMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<VideoComment> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.videoCommentMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<VideoComment> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.videoCommentMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(VideoComment bean, VideoCommentQuery param) {
        StringTools.checkParam(param);
        return this.videoCommentMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(VideoCommentQuery param) {
        StringTools.checkParam(param);
        return this.videoCommentMapper.deleteByParam(param);
    }

    /**
     * 根据CommentId获取对象（使用布隆过滤器防穿透）
     * 场景：评论查询频繁，防止恶意查询不存在的评论ID
     */
    @Override
    public VideoComment getVideoCommentByCommentId(Integer commentId) {
        // 1. 布隆过滤器判断
        if (!bloomFilterComponent.mightContainComment(commentId)) {
            log.debug("布隆过滤器判断评论不存在, commentId={}", commentId);
            return null;
        }

        // 2. 查询数据库
        VideoComment comment = this.videoCommentMapper.selectByCommentId(commentId);
        
        // 3. 无论是否存在，都加入布隆过滤器（防止重复穿透）
        bloomFilterComponent.addComment(commentId);
        
        return comment;
    }

    /**
     * 根据CommentId修改
     */
    @Override
    public Integer updateVideoCommentByCommentId(VideoComment bean, Integer commentId) {
        return this.videoCommentMapper.updateByCommentId(bean, commentId);
    }

    /**
     * 根据CommentId删除
     */
    @Override
    public Integer deleteVideoCommentByCommentId(Integer commentId) {
        return this.videoCommentMapper.deleteByCommentId(commentId);
    }

    @Override
    public void postComment(VideoComment comment, Integer replyCommentId) {

        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(comment.getVideoId());
        if (videoInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //是否关闭评论
        if (videoInfo.getInteraction() != null && videoInfo.getInteraction().contains(Constants.ZERO.toString())) {
            throw new BusinessException("UP主已关闭评论区");
        }


        if (replyCommentId != null) {
            VideoComment replyComment = getVideoCommentByCommentId(replyCommentId);
            //就是为了让评论同时指向一个视频
            if (replyComment == null || !replyComment.getVideoId().equals(comment.getVideoId())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }

            if(replyComment.getpCommentId()==0){
                comment.setpCommentId(replyComment.getCommentId());
            }
            else{
                comment.setpCommentId(replyComment.getpCommentId());
                comment.setReplyUserId(replyComment.getUserId());
            }

            UserInfo userInfo = userInfoMapper.selectByUserId(replyComment.getUserId());
            comment.setReplyNickName(userInfo.getNickName());
            comment.setReplyAvatar(userInfo.getAvatar());
        }
        else {
            comment.setpCommentId(0);
        }

        comment.setPostTime(new Date());
        comment.setVideoUserId(videoInfo.getUserId());
        videoCommentMapper.insert(comment);

        // 新增评论后，加入布隆过滤器
        bloomFilterComponent.addComment(comment.getCommentId());
        log.debug("新增评论，加入布隆过滤器，commentId={}", comment.getCommentId());

        if(comment.getpCommentId()==0){
            this.videoInfoMapper.updateCountInfo(comment.getVideoId(), UserActionTypeEnum.VIDEO_COMMENT.getField(), 1);
        }
    }

    @Override
    public void deleteComment(Integer commentId, String userId) {
        //看看视频和视频评论是否存在
        VideoComment comment = videoCommentMapper.selectByCommentId(commentId);
        if (comment==null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(comment.getVideoId());
        if (videoInfo==null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //权限最低。。
        if (userId != null && !videoInfo.getUserId().equals(userId) && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //删除父类评论
        videoCommentMapper.deleteByCommentId(commentId);
        videoInfoMapper.updateCountInfo(videoInfo.getVideoId(), UserActionTypeEnum.VIDEO_COMMENT.getField(), -1);
        //删除子类评论
        if (comment.getpCommentId() == 0) {
            //删除二级评论
            VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
            videoCommentQuery.setpCommentId(commentId);
            videoCommentMapper.deleteByParam(videoCommentQuery);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void topComment(Integer commentId, String userId) {
        cancelTopComment(commentId, userId);
        VideoComment videoComment = new VideoComment();
        videoComment.setTopType(CommentTopTypeEnum.TOP.getType());
        videoCommentMapper.updateByCommentId(videoComment, commentId);
    }


    @Override
    public void cancelTopComment(Integer commentId, String userId) {
        VideoComment dbVideoComment = videoCommentMapper.selectByCommentId(commentId);
        //看看是否能找到指定评论，找不到则报错
        if (dbVideoComment == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(dbVideoComment.getVideoId());
        //看看这个视频是否存在，如果存在则判断UP主是否是当前用户
        if (videoInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (!videoInfo.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        VideoComment videoComment = new VideoComment();
        videoComment.setTopType(CommentTopTypeEnum.NO_TOP.getType());

        VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
        videoCommentQuery.setVideoId(dbVideoComment.getVideoId());
        videoCommentQuery.setTopType(CommentTopTypeEnum.TOP.getType());

        //意思就是将置顶的评论取消置顶
        videoCommentMapper.updateByParam(videoComment, videoCommentQuery);
    }
}