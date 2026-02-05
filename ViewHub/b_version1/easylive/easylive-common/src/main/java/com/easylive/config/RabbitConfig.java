package com.easylive.config;

import com.easylive.entity.constant.Constants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 基础配置：声明交换机、队列与绑定关系。
 * 
 * 用于消息通知队列和视频播放统计队列的异步处理
 */
@Configuration
public class RabbitConfig {

    // ========== 用户消息通知队列 ==========
    
    //创建直连交换机
    @Bean
    public DirectExchange userMessageExchange() {
        // 持久化直连交换机
        return new DirectExchange(Constants.RABBITMQ_EXCHANGE_USER_MESSAGE, true, false);
    }
    
    //创建持久化队列
    @Bean
    public Queue userMessageQueue() {
        // 持久化队列
        return new Queue(Constants.RABBITMQ_QUEUE_USER_MESSAGE, true);
    }

    //创建绑定关系(把队列绑定到交换机)
    @Bean
    public Binding userMessageBinding(Queue userMessageQueue, DirectExchange userMessageExchange) {
        return BindingBuilder.bind(userMessageQueue)
            .to(userMessageExchange)
            .with(Constants.RABBITMQ_ROUTING_KEY_USER_MESSAGE);
    }

    // ========== 视频播放统计队列 ==========
    
    //创建直连交换机
    @Bean
    public DirectExchange videoPlayExchange() {
        // 持久化直连交换机
        return new DirectExchange(Constants.RABBITMQ_EXCHANGE_VIDEO_PLAY, true, false);
    }
    
    //创建持久化队列
    @Bean
    public Queue videoPlayQueue() {
        // 持久化队列
        return new Queue(Constants.RABBITMQ_QUEUE_VIDEO_PLAY, true);
    }

    //创建绑定关系(把队列绑定到交换机)
    @Bean
    public Binding videoPlayBinding(Queue videoPlayQueue, DirectExchange videoPlayExchange) {
        return BindingBuilder.bind(videoPlayQueue)
            .to(videoPlayExchange)
            .with(Constants.RABBITMQ_ROUTING_KEY_VIDEO_PLAY);
    }

    // ========== 视频转码队列（带死信队列） ==========
    
    /**
     * 创建转码死信交换机
     */
    @Bean
    public DirectExchange videoTransferDlxExchange() {
        return new DirectExchange(Constants.RABBITMQ_EXCHANGE_VIDEO_TRANSFER_DLX, true, false);
    }
    
    /**
     * 创建转码死信队列
     * 用于接收转码失败的消息
     */
    @Bean
    public Queue videoTransferDlxQueue() {
        return new Queue(Constants.RABBITMQ_QUEUE_VIDEO_TRANSFER_DLX, true);
    }
    
    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding videoTransferDlxBinding(Queue videoTransferDlxQueue, DirectExchange videoTransferDlxExchange) {
        return BindingBuilder.bind(videoTransferDlxQueue)
            .to(videoTransferDlxExchange)
            .with(Constants.RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER_DLX);
    }
    
    /**
     * 创建转码主交换机
     */
    @Bean
    public DirectExchange videoTransferExchange() {
        return new DirectExchange(Constants.RABBITMQ_EXCHANGE_VIDEO_TRANSFER, true, false);
    }
    
    /**
     * 创建转码队列
     * 配置死信队列参数：
     * - x-dead-letter-exchange: 死信交换机名称
     * - x-dead-letter-routing-key: 死信路由键
     * - x-message-ttl: 消息过期时间（毫秒），超过时间未消费则进入死信队列（可选）
     * - x-max-retries: 最大重试次数（可选，通过header实现）
     */
    @Bean
    public Queue videoTransferQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置死信交换机
        args.put("x-dead-letter-exchange", Constants.RABBITMQ_EXCHANGE_VIDEO_TRANSFER_DLX);
        // 设置死信路由键
        args.put("x-dead-letter-routing-key", Constants.RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER_DLX);
        // 设置消息过期时间：30分钟（转码任务超时）
        args.put("x-message-ttl", 30 * 60 * 1000);
        
        return QueueBuilder.durable(Constants.RABBITMQ_QUEUE_VIDEO_TRANSFER)
            .withArguments(args)
            .build();
    }
    
    /**
     * 绑定转码队列到转码交换机
     */
    @Bean
    public Binding videoTransferBinding(Queue videoTransferQueue, DirectExchange videoTransferExchange) {
        return BindingBuilder.bind(videoTransferQueue)
            .to(videoTransferExchange)
            .with(Constants.RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER);
    }

    // ========== 弹幕队列 ==========
    
    /**
     * 创建弹幕交换机
     */
    @Bean
    public DirectExchange videoDanmuExchange() {
        // 持久化直连交换机
        return new DirectExchange(Constants.RABBITMQ_EXCHANGE_VIDEO_DANMU, true, false);
    }
    
    /**
     * 创建弹幕队列
     */
    @Bean
    public Queue videoDanmuQueue() {
        // 持久化队列
        return new Queue(Constants.RABBITMQ_QUEUE_VIDEO_DANMU, true);
    }

    /**
     * 绑定弹幕队列到弹幕交换机
     */
    @Bean
    public Binding videoDanmuBinding(Queue videoDanmuQueue, DirectExchange videoDanmuExchange) {
        return BindingBuilder.bind(videoDanmuQueue)
            .to(videoDanmuExchange)
            .with(Constants.RABBITMQ_ROUTING_KEY_VIDEO_DANMU);
    }
}

