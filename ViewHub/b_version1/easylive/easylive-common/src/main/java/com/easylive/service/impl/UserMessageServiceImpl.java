package com.easylive.service.impl;

import com.easylive.entity.dto.UserMessageCountDto;
import com.easylive.entity.dto.UserMessageExtendDto;
import com.easylive.entity.enums.MessageReadTypeEnum;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.po.UserMessage;
import com.easylive.entity.po.VideoComment;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.po.VideoInfoPost;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.UserMessageQuery;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.query.VideoInfoPostQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.mappers.UserMessageMapper;
import com.easylive.mappers.VideoCommentMapper;
import com.easylive.mappers.VideoInfoPostMapper;
import com.easylive.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import com.easylive.utils.JsonUtils;
import com.easylive.utils.StringTools;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;


/**
 * 用户消息表 业务接口实现
 */
@Service("userMessageService")
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper<UserMessage, UserMessageQuery> userMessageMapper;

    private final VideoInfoPostMapper<VideoInfoPost, VideoInfoPostQuery> videoInfoPostMapper;

    private final VideoCommentMapper<VideoComment, VideoCommentQuery> videoCommentMapper;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserMessage> findListByParam(UserMessageQuery param) {
        return this.userMessageMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserMessageQuery param) {
        return this.userMessageMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserMessage> findListByPage(UserMessageQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserMessage> list = this.findListByParam(param);
        PaginationResultVO<UserMessage> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserMessage bean) {
        return this.userMessageMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserMessage> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userMessageMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserMessage> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userMessageMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserMessage bean, UserMessageQuery param) {
        StringTools.checkParam(param);
        return this.userMessageMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserMessageQuery param) {
        StringTools.checkParam(param);
        return this.userMessageMapper.deleteByParam(param);
    }

    /**
     * 根据MessageId获取对象
     */
    @Override
    public UserMessage getUserMessageByMessageId(Integer messageId) {
        return this.userMessageMapper.selectByMessageId(messageId);
    }

    /**
     * 根据MessageId修改
     */
    @Override
    public Integer updateUserMessageByMessageId(UserMessage bean, Integer messageId) {
        return this.userMessageMapper.updateByMessageId(bean, messageId);
    }

    /**
     * 根据MessageId删除
     */
    @Override
    public Integer deleteUserMessageByMessageId(Integer messageId) {
        return this.userMessageMapper.deleteByMessageId(messageId);
    }

    //保存用户消息
    // 注意：此方法由RabbitMQ消费者异步调用，无需再使用@Async注解
    @Override
    public void saveUserMessage(String videoId, String sendUserId, MessageTypeEnum messageTypeEnum, String content, Integer replyCommentId) {
        VideoInfo videoInfo = this.videoInfoPostMapper.selectByVideoId(videoId);   //获取指定的用户信息
        if (videoInfo == null) {
            return;
        }

        UserMessageExtendDto extendDto = new UserMessageExtendDto();
        extendDto.setMessageContent(content);

        String userId = videoInfo.getUserId();

        //收藏，点赞 已经记录过消息不再记录
        if (ArrayUtils.contains(new Integer[]{MessageTypeEnum.LIKE.getType(), MessageTypeEnum.COLLECTION.getType()}, messageTypeEnum.getType())) {
            // 构造查询条件：同一个用户、同一个视频、同一种消息类型
            UserMessageQuery userMessageQuery = new UserMessageQuery();
            userMessageQuery.setSendUserId(sendUserId);
            userMessageQuery.setVideoId(videoId);
            userMessageQuery.setMessageType(messageTypeEnum.getType());
            Integer count = userMessageMapper.selectCount(userMessageQuery);
            //如果找到有相同的记录，则终止
            if (count > 0) {
                return;
            }
        }

        UserMessage userMessage = new UserMessage();
        userMessage.setUserId(userId);
        userMessage.setVideoId(videoId);
        userMessage.setReadType(MessageReadTypeEnum.NO_READ.getType());
        userMessage.setCreateTime(new Date());
        userMessage.setMessageType(messageTypeEnum.getType());
        userMessage.setSendUserId(sendUserId);

        // 如果replyCommentId不为空，说明这是一条回复评论的操作。如果存在的话，则会保存到扩展信息中，用于消息展示
        if (replyCommentId != null) {
            VideoComment commentInfo = videoCommentMapper.selectByCommentId(replyCommentId);
            if (commentInfo!=null) {
                userId = commentInfo.getUserId();
                extendDto.setMessageContentReply(commentInfo.getContent());
            }
        }
        // 如果操作发起者和消息接收者是同一人，则不发送消息
        if (userId.equals(sendUserId)) {
            return;
        }

        //系统消息特殊处理。。。也就是管理员审核的消息。
        if (messageTypeEnum == MessageTypeEnum.SYS) {
            VideoInfoPost videoInfoPost = videoInfoPostMapper.selectByVideoId(videoId);
            extendDto.setAuditStatus(videoInfoPost.getStatus());  //保存审核状态
        }
        userMessage.setUserId(userId);
        userMessage.setExtendJson(JsonUtils.convertObj2Json(extendDto));  //将extendDto转化成json字符串保存到数据库中

        //保存到数据库中
        this.userMessageMapper.insert(userMessage);
    }

    @Override
    public List<UserMessageCountDto> getMessageTypeNoReadCount(String userId) {
        return this.userMessageMapper.getMessageTypeNoReadCount(userId);
    }
}