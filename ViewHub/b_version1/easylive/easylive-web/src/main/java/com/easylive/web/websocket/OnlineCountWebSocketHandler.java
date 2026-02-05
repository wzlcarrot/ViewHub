package com.easylive.web.websocket;

import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线人数WebSocket处理器
 * 
 * 功能：
 * 1. 管理WebSocket连接（按fileId分组）
 * 2. 实时推送在线人数变化
 * 3. 使用Redis Pub/Sub实现多实例广播
 * 4. 心跳检测保持连接活跃
 * 
 * 技术亮点（校招水平）：
 * - WebSocket长连接，减少HTTP请求
 * - Redis计数器原子操作
 * - Redis Pub/Sub多实例广播
 * - 连接池管理（ConcurrentHashMap）
 * - 心跳检测机制
 * - SockJS降级支持
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OnlineCountWebSocketHandler extends TextWebSocketHandler {

    // 按fileId分组管理WebSocket连接
    // Key: fileId, Value: 该fileId下的所有WebSocket连接
    private static final ConcurrentHashMap<String, Set<WebSocketSession>> onlineRooms = new ConcurrentHashMap<>();

    // 管理每个频道的Redis监听器（用于取消订阅）
    // Key: fileId, Value: MessageListener
    private static final ConcurrentHashMap<String, MessageListener> channelListeners = new ConcurrentHashMap<>();

    private final RedisComponent redisComponent;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisMessageListenerContainer redisMessageListenerContainer;

    // Redis Pub/Sub频道前缀
    private static final String REDIS_CHANNEL_PREFIX = "online:count:";

    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        log.info("在线人数WebSocket处理器初始化完成");
    }

    /**
     * WebSocket连接建立时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String fileId = getFileIdFromSession(session);
        String deviceId = getDeviceIdFromSession(session);
        
        if (fileId == null || fileId.isEmpty()) {
            log.warn("WebSocket连接建立失败：无法获取fileId, sessionId={}", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 1. 加入房间
        onlineRooms.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet()).add(session);

        // 2. 上报在线（Redis计数器+1）
        Integer count = redisComponent.reportVideoPlayOnline(fileId, deviceId);
        log.debug("用户上线, fileId={}, deviceId={}, 当前在线人数={}", fileId, deviceId, count);

        // 3. 推送当前在线人数
        sendMessage(session, createOnlineCountMessage(count));

        // 4. 订阅Redis频道（接收其他实例的在线人数变化）
        subscribeRedisChannel(fileId);

        // 5. 广播人数变化（通知其他连接）
        broadcastOnlineCount(fileId);

        log.info("在线人数WebSocket连接建立, fileId={}, deviceId={}, sessionId={}, 当前房间连接数={}", 
            fileId, deviceId, session.getId(), onlineRooms.get(fileId).size());
    }

    /**
     * WebSocket连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String fileId = getFileIdFromSession(session);
        if (fileId == null) {
            return;
        }

        Set<WebSocketSession> sessions = onlineRooms.get(fileId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                onlineRooms.remove(fileId);
                // 取消订阅Redis频道
                unsubscribeRedisChannel(fileId);
            }
        }

        // 减少在线人数
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId != null) {
            String userKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER, fileId, deviceId);
            redisComponent.deleteUserOnlineKey(userKey);
        }
        
        String countKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE, fileId);
        redisComponent.decrementOnlineCount(countKey);

        // 广播人数变化
        broadcastOnlineCount(fileId);

        log.info("在线人数WebSocket连接关闭, fileId={}, deviceId={}, sessionId={}, 状态={}", 
            fileId, deviceId, session.getId(), status);
    }

    /**
     * 处理接收到的WebSocket消息（心跳检测）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        // 心跳检测：前端发送 {"type":"ping"}
        if (payload != null && payload.contains("\"type\":\"ping\"")) {
            sendMessage(session, "{\"type\":\"pong\"}");
            log.debug("收到心跳消息, sessionId={}", session.getId());
        }
    }

    /**
     * 处理WebSocket错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误, sessionId={}", session.getId(), exception);
        String fileId = getFileIdFromSession(session);
        if (fileId != null) {
            Set<WebSocketSession> sessions = onlineRooms.get(fileId);
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    /**
     * 广播在线人数变化（供外部调用）
     * 
     * @param fileId 文件ID
     */
    public void broadcastOnlineCount(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return;
        }

        try {
            String countKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE, fileId);
            Integer count = redisComponent.getOnlineCount(countKey);

            String message = createOnlineCountMessage(count);

            // 1. 广播给当前实例的所有连接
            broadcastToLocalSessions(fileId, message);

            // 2. 发布到Redis，通知其他实例
            String channel = REDIS_CHANNEL_PREFIX + fileId;
            redisTemplate.convertAndSend(channel, count.toString());

            log.debug("在线人数已广播, fileId={}, count={}", fileId, count);

        } catch (Exception e) {
            log.error("广播在线人数失败, fileId={}", fileId, e);
        }
    }

    /**
     * 广播消息给本地连接
     */
    private void broadcastToLocalSessions(String fileId, String message) {
        Set<WebSocketSession> sessions = onlineRooms.get(fileId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 并发安全地遍历并发送消息
        sessions.removeIf(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    return false;  // 保留连接
                } else {
                    return true;  // 移除已关闭的连接
                }
            } catch (IOException e) {
                log.warn("发送WebSocket消息失败, sessionId={}", session.getId(), e);
                return true;  // 移除异常连接
            }
        });
    }

    /**
     * 订阅Redis频道（接收其他实例的在线人数变化）
     */
    private void subscribeRedisChannel(String fileId) {
        // 如果已经订阅过，直接返回
        if (channelListeners.containsKey(fileId)) {
            return;
        }

        String channel = REDIS_CHANNEL_PREFIX + fileId;
        ChannelTopic topic = new ChannelTopic(channel);

        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String countStr = new String(message.getBody());
                    Integer count = Integer.parseInt(countStr);
                    String wsMessage = createOnlineCountMessage(count);
                    
                    // 广播给当前实例的所有连接
                    broadcastToLocalSessions(fileId, wsMessage);
                    
                    log.debug("收到Redis在线人数消息, fileId={}, count={}", fileId, count);
                } catch (Exception e) {
                    log.error("处理Redis在线人数消息失败, fileId={}", fileId, e);
                }
            }
        };

        // 保存监听器引用，用于后续取消订阅
        channelListeners.put(fileId, listener);
        redisMessageListenerContainer.addMessageListener(listener, topic);
        log.debug("已订阅Redis频道, fileId={}, channel={}", fileId, channel);
    }

    /**
     * 取消订阅Redis频道
     */
    private void unsubscribeRedisChannel(String fileId) {
        MessageListener listener = channelListeners.remove(fileId);
        if (listener != null) {
            String channel = REDIS_CHANNEL_PREFIX + fileId;
            ChannelTopic topic = new ChannelTopic(channel);
            redisMessageListenerContainer.removeMessageListener(listener, topic);
            log.debug("已取消订阅Redis频道, fileId={}, channel={}", fileId, channel);
        }
    }

    /**
     * 从WebSocket Session中提取fileId
     * 路径格式：/ws/online/{fileId}
     */
    private String getFileIdFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) {
                return null;
            }
            String path = uri.getPath();
            // 路径格式：/ws/online/{fileId} 或 /ws/online/{fileId}/websocket (SockJS)
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty() && !parts[i].equals("websocket") && !parts[i].equals("online")) {
                    return parts[i];
                }
            }
            return null;
        } catch (Exception e) {
            log.error("提取fileId失败", e);
            return null;
        }
    }

    /**
     * 从WebSocket Session中提取deviceId
     * 查询参数格式：?deviceId=xxx
     */
    private String getDeviceIdFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                return null;
            }
            String query = uri.getQuery();
            String[] params = query.split("&");
            for (String param : params) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "deviceId".equals(kv[0])) {
                    return kv[1];
                }
            }
            return null;
        } catch (Exception e) {
            log.error("提取deviceId失败", e);
            return null;
        }
    }

    /**
     * 创建在线人数消息
     */
    private String createOnlineCountMessage(Integer count) {
        return String.format("{\"type\":\"onlineCount\",\"count\":%d}", count);
    }

    /**
     * 发送消息给指定连接
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("发送WebSocket消息失败, sessionId={}", session.getId(), e);
        }
    }

    /**
     * 获取当前房间的连接数（用于监控）
     */
    public int getRoomConnectionCount(String fileId) {
        Set<WebSocketSession> sessions = onlineRooms.get(fileId);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * 获取所有房间的连接数（用于监控）
     */
    public int getTotalConnectionCount() {
        return onlineRooms.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * 清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("在线人数WebSocket处理器销毁, 当前总连接数={}", getTotalConnectionCount());
    }
}

