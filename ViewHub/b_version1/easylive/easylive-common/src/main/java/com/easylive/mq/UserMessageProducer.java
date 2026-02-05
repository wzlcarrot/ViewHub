package com.easylive.mq;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.UserMessageTaskDTO;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户消息任务消息生产者
 * 
 * 职责：将用户消息任务发送到 RabbitMQ 消息队列
 * 
 * 使用场景：
 * 1. 当用户进行点赞、收藏、评论等操作时，异步发送消息通知
 * 2. 避免同步处理影响接口响应时间
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送用户消息任务到消息队列
     * 
     * @param taskDTO 用户消息任务DTO
     */
    public void sendUserMessageTask(UserMessageTaskDTO taskDTO) {
        try {
            // 将对象转换为 JSON 字符串
            String messageBody = JsonUtils.convertObj2Json(taskDTO);

            // 发送消息到 RabbitMQ（Direct 交换机）
            rabbitTemplate.convertAndSend(
                Constants.RABBITMQ_EXCHANGE_USER_MESSAGE,
                Constants.RABBITMQ_ROUTING_KEY_USER_MESSAGE,
                messageBody
            );

            log.info("用户消息任务已发送到MQ, videoId={}, sendUserId={}, messageType={}", 
                taskDTO.getVideoId(), taskDTO.getSendUserId(), taskDTO.getMessageType());
        } catch (Exception e) {
            log.error("发送用户消息任务到MQ失败, videoId={}, sendUserId={}", 
                taskDTO.getVideoId(), taskDTO.getSendUserId(), e);
            throw new RuntimeException("发送消息到MQ失败", e);
        }
    }
}

