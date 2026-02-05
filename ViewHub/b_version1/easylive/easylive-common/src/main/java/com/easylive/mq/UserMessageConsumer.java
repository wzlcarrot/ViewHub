package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.UserMessageTaskDTO;
import com.easylive.entity.enums.MessageTypeEnum;
import com.easylive.service.UserMessageService;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 用户消息任务消息消费者
 * 
 * 职责：监听 RabbitMQ 消息，处理用户消息通知任务
 * 
 * 工作流程：
 * 1. 监听 MQ 中的用户消息任务消息
 * 2. 解析消息内容
 * 3. 调用服务层保存用户消息
 * 
 * @RabbitListener 注解说明：
 * - queues: 监听的队列名称
 * - concurrency: 并发消费线程数（可选）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserMessageConsumer {

    private final UserMessageService userMessageService;

    /**
     * 处理接收到的消息
     *
     * @param message JSON 格式的 UserMessageTaskDTO 字符串
     */
    @RabbitListener(queues = Constants.RABBITMQ_QUEUE_USER_MESSAGE)
    public void onMessage(String message) {
        try {
            // 将 JSON 字符串转换为对象
            UserMessageTaskDTO taskDTO = JsonUtils.convertJson2Obj(message, UserMessageTaskDTO.class);
            
            if (taskDTO == null) {
                log.error("消息解析失败，message={}", message);
                return;
            }

            log.info("收到用户消息任务消息, videoId={}, sendUserId={}, messageType={}", 
                taskDTO.getVideoId(), taskDTO.getSendUserId(), taskDTO.getMessageType());

            // 将Integer转换为MessageTypeEnum
            MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(taskDTO.getMessageType());
            if (messageTypeEnum == null) {
                log.error("消息类型无效, messageType={}", taskDTO.getMessageType());
                return;
            }

            // 调用服务层保存用户消息
            userMessageService.saveUserMessage(
                taskDTO.getVideoId(),
                taskDTO.getSendUserId(),
                messageTypeEnum,
                taskDTO.getContent(),
                taskDTO.getReplyCommentId()
            );

            log.info("用户消息任务处理成功, videoId={}, sendUserId={}", 
                taskDTO.getVideoId(), taskDTO.getSendUserId());

        } catch (Exception e) {
            log.error("消费用户消息任务消息失败, message={}", message, e);
        }
    }
}

