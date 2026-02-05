package com.easylive.web.component;

import com.easylive.component.RedisComponent;

import com.easylive.entity.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;


@Component
@Slf4j
//使用springboot redis这个类监听redis的key过期事件
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {
    private final RedisComponent redisComponent;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer, RedisComponent redisComponent) {
        super(listenerContainer);
        this.redisComponent = redisComponent;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        //如果不是user的 key，则终止方法
        if (!key.startsWith(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREFIX + Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX)) {
            return;
        }
        //监听
        Integer userKeyIndex = key.indexOf(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX.length())+Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX.length();
        //文件名的长度是20，把他提取出来
        String fileId = key.substring(userKeyIndex, userKeyIndex + Constants.length_20);

        //减少在线观看人数
        redisComponent.decrementPlayOnlineCount(String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE, fileId));
    }
}
