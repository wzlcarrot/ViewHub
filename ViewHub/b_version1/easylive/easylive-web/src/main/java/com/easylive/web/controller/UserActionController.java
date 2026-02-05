package com.easylive.web.controller;


import com.easylive.annotation.RecordUserMessage;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.entity.po.UserAction;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.redis.RedisUtils;
import com.easylive.service.UserActionService;
import com.easylive.web.annotation.GlobalInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.*;


@RestController
@RequestMapping("/userAction")
@RequiredArgsConstructor
public class UserActionController extends ABaseController{

    private final UserActionService userActionService;

    private final RedisUtils<Object> redisUtils;

    @RequestMapping("/doAction")
    @GlobalInterceptor(checkLogin = true)
    @RecordUserMessage(messageType= MessageTypeEnum.LIKE)
    public ResponseVO doAction(@NotEmpty String videoId,
                               @NotEmpty Integer actionType,
                               @Max(2) @Min(1) Integer actionCount,
                               Integer commentId,
                               @RequestParam(value = "requestId", required = false) String requestId) {

        // 基于 Redis Set 的接口幂等：前端可传入 requestId，避免重复点击/重试导致多次写入
        if (requestId != null && requestId.trim().length() > 0) {
            String idemKey = Constants.REDIS_KEY_USER_ACTION_IDEMPOTENT_PREFIX + getTokenUserInfoDto().getUserId();
            boolean first = redisUtils.sAdd(idemKey, requestId, Constants.REDIS_KEY_EXPIRE_ONE_DAY.longValue());
            if (!first) {
                // 已经处理过的请求，直接返回成功，避免重复写入（幂等）
                return getSuccessResponseVO(null);
            }
        }
        UserAction userAction = new UserAction();
        userAction.setUserId(getTokenUserInfoDto().getUserId());
        userAction.setVideoId(videoId);
        userAction.setActionType(actionType);
        actionCount = actionCount == null ? Constants.ONE : actionCount;
        userAction.setActionCount(actionCount);
        commentId = commentId == null ? 0 : commentId;
        userAction.setCommentId(commentId);
        userActionService.saveAction(userAction);
        return getSuccessResponseVO(null);
    }
}
