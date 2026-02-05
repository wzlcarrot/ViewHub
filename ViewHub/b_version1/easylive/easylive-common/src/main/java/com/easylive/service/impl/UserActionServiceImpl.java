package com.easylive.service.impl;

import com.easylive.component.EsSearchComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.UserAction;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.po.VideoComment;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.*;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.UserActionMapper;
import com.easylive.mappers.UserInfoMapper;
import com.easylive.mappers.VideoCommentMapper;
import com.easylive.mappers.VideoInfoMapper;
import com.easylive.service.UserActionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;


/**
 * 用户行为 点赞、评论 业务接口实现
 */
@Service("userActionService")
@Slf4j
@RequiredArgsConstructor
public class UserActionServiceImpl implements UserActionService {

    private final UserActionMapper<UserAction, UserActionQuery> userActionMapper;

    private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoCommentMapper<VideoComment, VideoCommentQuery> videoCommentMapper;

    private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    private final EsSearchComponent esSearchComponent;

    private final RedisUtils<Object> redisUtils;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserAction> findListByParam(UserActionQuery param) {
        return this.userActionMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserActionQuery param) {
        return this.userActionMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserAction> findListByPage(UserActionQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserAction> list = this.findListByParam(param);
        PaginationResultVO<UserAction> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserAction bean) {
        return this.userActionMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserAction> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userActionMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserAction> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userActionMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserAction bean, UserActionQuery param) {
        StringTools.checkParam(param);
        return this.userActionMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserActionQuery param) {
        StringTools.checkParam(param);
        return this.userActionMapper.deleteByParam(param);
    }

    /**
     * 根据ActionId获取对象
     */
    @Override
    public UserAction getUserActionByActionId(Integer actionId) {
        return this.userActionMapper.selectByActionId(actionId);
    }

    /**
     * 根据ActionId修改
     */
    @Override
    public Integer updateUserActionByActionId(UserAction bean, Integer actionId) {
        return this.userActionMapper.updateByActionId(bean, actionId);
    }

    /**
     * 根据ActionId删除
     */
    @Override
    public Integer deleteUserActionByActionId(Integer actionId) {
        return this.userActionMapper.deleteByActionId(actionId);
    }

    /**
     * 根据VideoIdAndCommentIdAndActionTypeAndUserId获取对象
     */
    @Override
    public UserAction getUserActionByVideoIdAndCommentIdAndActionTypeAndUserId(String videoId, Integer commentId, Integer actionType, String userId) {
        return this.userActionMapper.selectByVideoIdAndCommentIdAndActionTypeAndUserId(videoId, commentId, actionType, userId);
    }

    /**
     * 根据VideoIdAndCommentIdAndActionTypeAndUserId修改
     */
    @Override
    public Integer updateUserActionByVideoIdAndCommentIdAndActionTypeAndUserId(UserAction bean, String videoId, Integer commentId, Integer actionType, String userId) {
        return this.userActionMapper.updateByVideoIdAndCommentIdAndActionTypeAndUserId(bean, videoId, commentId, actionType, userId);
    }

    /**
     * 根据VideoIdAndCommentIdAndActionTypeAndUserId删除
     */
    @Override
    public Integer deleteUserActionByVideoIdAndCommentIdAndActionTypeAndUserId(String videoId, Integer commentId, Integer actionType, String userId) {
        return this.userActionMapper.deleteByVideoIdAndCommentIdAndActionTypeAndUserId(videoId, commentId, actionType, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAction(UserAction bean) {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(bean.getVideoId());
        if (videoInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        bean.setVideoUserId(videoInfo.getUserId());

        UserActionTypeEnum actionTypeEnum = UserActionTypeEnum.getByType(bean.getActionType());
        if (actionTypeEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        UserAction dbAction = userActionMapper.selectByVideoIdAndCommentIdAndActionTypeAndUserId(bean.getVideoId(), bean.getCommentId(), bean.getActionType(),
                bean.getUserId());
        bean.setActionTime(new Date());


        switch (actionTypeEnum) {
            case VIDEO_LIKE:
            case VIDEO_COLLECT:
                if (dbAction != null) {
                    // 已存在记录，再次点击视为取消，删除原记录
                    userActionMapper.deleteByActionId(dbAction.getActionId());
                } else {
                    // 第一次行为，写入明细表（同步保证查询准确）
                    userActionMapper.insert(bean);
                }
                // null 表示之前没有行为，否则就是之前有行为了（用于计数增量）
                Integer changeCount = dbAction == null ? 1 : -1;

                // 计数改为异步写 + 写聚合：优先写入 Redis Hash，失败时降级写数据库
                String hashKey;
                if (actionTypeEnum == UserActionTypeEnum.VIDEO_LIKE) {
                    hashKey = Constants.REDIS_KEY_VIDEO_LIKE_HASH;
                } else {
                    hashKey = Constants.REDIS_KEY_VIDEO_COLLECT_HASH;
                }
                
                // 尝试写入Redis，失败时降级直接写数据库（兜底方案）
                try {
                    redisUtils.hIncrBy(hashKey, bean.getVideoId(), changeCount);
                } catch (Exception e) {
                    // Redis写入失败，降级直接写数据库，保证数据不丢失
                    log.error("Redis写入失败，降级写数据库, videoId={}, actionType={}, changeCount={}", 
                            bean.getVideoId(), actionTypeEnum, changeCount, e);
                    videoInfoMapper.updateCountInfo(bean.getVideoId(), actionTypeEnum.getField(), changeCount);
                    // 如果是收藏操作，同时更新ES
                    if (actionTypeEnum == UserActionTypeEnum.VIDEO_COLLECT) {
                        esSearchComponent.updateDocCount(bean.getVideoId(), 
                                SearchOrderTypeEnum.VIDEO_COLLECT.getField(), changeCount);
                    }
                }

                // 说明：
                // - 正常情况下：DB 中的 likeCount / collectCount 通过定时任务批量刷新
                // - Redis异常时：降级直接写数据库，保证数据不丢失（兜底方案）
                // - ES 中的排序字段（收藏数）同样通过聚合任务更新，或降级时直接更新
                break;
            case VIDEO_COIN:
                if(videoInfo.getUserId().equals(bean.getUserId())){
                    throw new BusinessException("up主不能跟自己投币");
                }
                if(dbAction!=null){
                    throw new BusinessException("对本稿件的投币枚数已经用完");
                }

                Integer updateCount = userInfoMapper.updateCoinCountInfo(bean.getUserId(), -bean.getActionCount());

                if(updateCount==0){
                    throw new BusinessException("用户硬币数量不足");
                }

                updateCount = userInfoMapper.updateCoinCountInfo(videoInfo.getUserId(), bean.getActionCount());

                if(updateCount==0){
                    throw new BusinessException("给up主投币失败");
                }

                userActionMapper.insert(bean);
                videoInfoMapper.updateCountInfo(bean.getVideoId(), actionTypeEnum.getField(), bean.getActionCount());
                break;

            case COMMENT_LIKE:
            case COMMENT_HATE:
                UserActionTypeEnum opposeTypeEnum;   //对立面的操作类型
                if (actionTypeEnum == UserActionTypeEnum.COMMENT_LIKE) {
                    opposeTypeEnum = UserActionTypeEnum.COMMENT_HATE;
                } else {
                    opposeTypeEnum = UserActionTypeEnum.COMMENT_LIKE;
                }

                //看看是否存在对立面行为
                UserAction opposeAction = userActionMapper.selectByVideoIdAndCommentIdAndActionTypeAndUserId(bean.getVideoId(), bean.getCommentId(),
                        opposeTypeEnum.getType(), bean.getUserId());

                //比如你之前点了"踩"，现在点"赞"，就把之前的"踩"删除
                if(opposeAction!=null){
                    userActionMapper.deleteByActionId(opposeAction.getActionId());

                }
                //比如你之前点了"赞"，现在再点"赞"，就把之前的"赞"删除（取消点赞）
                if(dbAction!=null){
                    userActionMapper.deleteByActionId(dbAction.getActionId());
                }
                else{
                    userActionMapper.insert(bean);
                }
                //等于null，说明是第一次
                changeCount = dbAction == null ? 1 : -1;
                Integer opposeChangeCount = changeCount * -1;

                //更新评论的点赞数或者踩数
                videoCommentMapper.updateCountInfo(bean.getCommentId(),
                        actionTypeEnum.getField(),
                        changeCount,
                        opposeAction == null ? null : opposeTypeEnum.getField(),
                        opposeChangeCount);
                break;




        }
    }
}