package com.easylive.web.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 用于配置 AI 模型
 */
@Configuration
public class LangChainConfig {

    private static final Logger logger = LoggerFactory.getLogger(LangChainConfig.class);

    @Value("${langchain.openai.api-key:demo}")
    private String apiKey;

    @Value("${langchain.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${langchain.openai.model:gpt-3.5-turbo}")
    private String modelName;

    /**
     * 配置 OpenAI 聊天模型
     * 使用 LangChain4j 官方测试 API Key
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        logger.info("初始化 ChatLanguageModel，API Key: {}, Base URL: {}, Model: {}", 
                    apiKey.substring(0, Math.min(10, apiKey.length())) + "...", 
                    baseUrl, 
                    modelName);
        
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)  // 增加随机性，避免重复回复
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .logRequests(false)  // 生产环境建议关闭
                .logResponses(false)
                .build();
    }
}

