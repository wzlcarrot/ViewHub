package com.easylive.web.controller;

import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.web.service.AIChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 聊天机器人 Controller
 * 提供智能对话接口（RAG模式）
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIChatController extends ABaseController {

    private static final Logger logger = LoggerFactory.getLogger(AIChatController.class);

    private final AIChatService aiChatService;

    /**
     * 发送聊天消息
     * @param message 用户消息
     * @return AI回复
     */
    @PostMapping("/chat")
    public ResponseVO chat(@RequestParam String message) {
        try {
            // 从token获取用户ID（如果未登录，tokenUserInfoDto为null）
            TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
            String userId;
            
            if (tokenUserInfoDto != null) {
                // 已登录用户，使用token中的用户ID
                userId = tokenUserInfoDto.getUserId();
            } else {
                // 未登录用户，使用请求的IP和时间戳作为标识
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                String clientIp = request.getRemoteAddr();
                userId = "guest_" + clientIp + "_" + System.currentTimeMillis();
            }
            
            logger.info("收到聊天请求，用户ID: {}, 消息: {}", userId, message);
            
            // 调用AI服务
            String response = aiChatService.chat(userId, message);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", response);
            result.put("timestamp", System.currentTimeMillis());
            
            return getSuccessResponseVO(result);
            
        } catch (Exception e) {
            logger.error("聊天处理失败", e);
            return getServerErrorResponseVO("聊天服务暂时不可用，请稍后再试");
        }
    }
}

