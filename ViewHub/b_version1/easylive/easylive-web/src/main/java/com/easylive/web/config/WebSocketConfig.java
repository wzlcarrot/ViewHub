package com.easylive.web.config;

import com.easylive.web.websocket.DanmuWebSocketHandler;
import com.easylive.web.websocket.OnlineCountWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 
 * 功能：
 * 1. 配置弹幕实时推送的WebSocket端点
 * 2. 配置在线人数实时推送的WebSocket端点
 * 3. 支持SockJS降级（兼容性更好）
 * 4. 允许跨域访问
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DanmuWebSocketHandler danmuWebSocketHandler;

    private final OnlineCountWebSocketHandler onlineCountWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册弹幕WebSocket处理器
        // 路径：/ws/danmu/{fileId}
        // 支持SockJS降级，允许跨域
        registry.addHandler(danmuWebSocketHandler, "/ws/danmu/{fileId}")
                .setAllowedOrigins("*")  // 允许跨域
                .withSockJS();  // 支持SockJS降级（当WebSocket不可用时，自动降级到HTTP轮询）

        // 注册在线人数WebSocket处理器
        // 路径：/ws/online/{fileId}?deviceId=xxx
        // 支持SockJS降级，允许跨域
        registry.addHandler(onlineCountWebSocketHandler, "/ws/online/{fileId}")
                .setAllowedOrigins("*")  // 允许跨域
                .withSockJS();  // 支持SockJS降级
    }
}

