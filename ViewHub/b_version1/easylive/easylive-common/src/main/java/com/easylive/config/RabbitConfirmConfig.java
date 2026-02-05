package com.easylive.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * RabbitMQ 生产者确认机制配置
 * 
 * 功能：确保消息成功发送到 RabbitMQ，防止消息丢失
 * 
 * 面试要点：
 * 1. Producer Confirm：确认消息是否到达交换机
 * 2. Returns Callback：处理无法路由的消息
 * 3. 重点处理转码任务（关键业务）
 */
@Configuration
@Slf4j
public class RabbitConfirmConfig {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void initRabbitTemplate() {
        // 确认回调：消息发送到交换机后的确认
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // 消息成功发送到交换机
                log.debug("消息发送成功, messageId={}", 
                    correlationData != null ? correlationData.getId() : "unknown");
            } else {
                // 消息发送失败
                String messageId = correlationData != null ? correlationData.getId() : "unknown";
                log.error("消息发送失败, messageId={}, cause={}", messageId, cause);
                
                // 如果是转码任务，记录详细日志
                if (messageId != null && messageId.startsWith("TRANSFER_")) {
                    log.error("【重要】转码任务发送失败, messageId={}", messageId);
                }
            }
        });

        // 返回回调：消息无法路由到队列时触发
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息无法路由到队列, exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText());
        });
    }
}

