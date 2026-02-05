package com.easylive.aspect;

import com.easylive.annotation.RecordUserMessage;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UserMessageTaskDTO;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.mq.UserMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Component
@Aspect
@Slf4j
@RequiredArgsConstructor
public class UserMessageOperationAspect {

    private final RedisComponent redisComponent;

    private final UserMessageProducer userMessageProducer;

    private static final String PARAMETERS_VIDEO_ID = "videoId";

    private static final String PARAMETERS_ACTION_TYPE = "actionType";

    private static final String PARAMETERS_REPLY_COMMENTID = "replyCommentId";

    private static final String PARAMETERS_AUDIT_REJECT_REASON = "reason";

    private static final String PARAMETERS_CONTENT = "content";

    @Around("@annotation(com.easylive.annotation.RecordUserMessage)")
    public ResponseVO interceptorDoc(ProceedingJoinPoint point) throws Throwable {
        ResponseVO responseVO = (ResponseVO) point.proceed();

        Method method = ((MethodSignature)point.getSignature()).getMethod();
        //获取方法上的 @RecordUserMessage 注解
        RecordUserMessage recordUserMessage = method.getAnnotation(RecordUserMessage.class);  //通过注解拿到这个类的所有属性,对象

        //如果方法上有这个注解，则保存用户操作信息
        if(recordUserMessage!=null){
            /*
            * 1.通过它可以获取注解中定义的属性值，比如操作类型、描述等信息
            * 2.目标方法时传入的实际参数值
            * 3.目标方法的参数定义信息
            * */
            saveMessage(recordUserMessage, point.getArgs(), method.getParameters());
        }
        return responseVO;
    }

    //around注解方法定义的切面可以通过这种方式获取相关方法的属性值。这就是aop的强大之处
    private void saveMessage(RecordUserMessage recordUserMessage, Object[] arguments, Parameter[] parameters){
        System.out.println("recordUserMessage:"+recordUserMessage+" "+"arguments:"+arguments+" "+"parameters:"+parameters);

        String videoId = null;
        Integer actionType = null;
        Integer replyCommentId = null;
        String content = null;

        for(int i=0;i<parameters.length;i++){
            if(PARAMETERS_VIDEO_ID.equals(parameters[i].getName())){
                videoId = arguments[i].toString();
            }
            else if(PARAMETERS_ACTION_TYPE.equals(parameters[i].getName())){
                actionType = (Integer) arguments[i];
            }
            else if(PARAMETERS_REPLY_COMMENTID.equals(parameters[i].getName())){
                replyCommentId = (Integer) arguments[i];
            }
            else if(PARAMETERS_AUDIT_REJECT_REASON.equals(parameters[i].getName())){
                content = arguments[i].toString();
            }
            else if(PARAMETERS_CONTENT.equals(parameters[i].getName())){
                content = arguments[i].toString();
            }
            else{
                log.error("参数名称有误");
            }
        }

        //判断一下此时进行的是什么操作，，是点赞还是收藏，还是其他
        MessageTypeEnum messageTypeEnum = recordUserMessage.messageType();
        if (UserActionTypeEnum.VIDEO_COLLECT.getType().equals(actionType)) {
            messageTypeEnum = MessageTypeEnum.COLLECTION;
        }

        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        
        // 如果tokenUserInfoDto为null，说明用户未登录或token已过期，不发送消息
        if (tokenUserInfoDto == null) {
            log.warn("用户未登录或token已过期，跳过消息发送, videoId={}", videoId);
            return;
        }

        // 构建消息任务DTO
        UserMessageTaskDTO taskDTO = new UserMessageTaskDTO();
        taskDTO.setVideoId(videoId);
        taskDTO.setSendUserId(tokenUserInfoDto.getUserId());
        taskDTO.setMessageType(messageTypeEnum.getType());
        taskDTO.setContent(content);
        taskDTO.setReplyCommentId(replyCommentId);

        // 发送消息到RabbitMQ（异步处理）
        try {
            userMessageProducer.sendUserMessageTask(taskDTO);
            log.debug("用户消息任务已发送到MQ, videoId={}, sendUserId={}, messageType={}", 
                videoId, tokenUserInfoDto.getUserId(), messageTypeEnum.getType());
        } catch (Exception e) {
            log.error("发送用户消息任务到MQ失败, videoId={}, sendUserId={}", 
                videoId, tokenUserInfoDto.getUserId(), e);
            // 发送失败不影响主流程，只记录日志
        }
    }

    private TokenUserInfoDto getTokenUserInfoDto(){
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                log.warn("RequestContextHolder.getRequestAttributes() 返回 null");
                return null;
            }
            
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                log.warn("ServletRequestAttributes.getRequest() 返回 null");
                return null;
            }
            
            String token = request.getHeader(Constants.TOKEN_WEB);
            if (token == null || token.isEmpty()) {
                log.warn("请求头中未找到 token");
                return null;
            }
            
            return redisComponent.getTokenInfo(token);
        } catch (Exception e) {
            log.error("获取用户token信息失败", e);
            return null;
        }
    }

}
