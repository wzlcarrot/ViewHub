package com.easylive.web.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
// 尝试使用反射加载Embedding模型（避免导入路径问题）
// import dev.langchain4j.model.embedding.all.minilml6v2.AllMiniLmL6V2EmbeddingModel;

import dev.langchain4j.model.output.Response;
import com.easylive.component.EsSearchComponent;
import com.easylive.entity.dto.VectorBlock;
import com.easylive.entity.po.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.scheduling.annotation.Async;
import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 聊天服务
 * 使用 LangChain4j 实现智能对话
 * 
 * 设计特点：
 * 1. 不保存历史记录，每次都是新对话（无状态设计）
 * 2. 采用RAG模式，通过向量检索增强AI回答准确性
 * 3. 支持预索引策略，减少首次回答等待时间
 */
@Service
@RequiredArgsConstructor
public class AIChatService {

    private static final Logger logger = LoggerFactory.getLogger(AIChatService.class);

    private final ChatLanguageModel chatLanguageModel;

    private final VideoKnowledgeService videoKnowledgeService;
    
    private final EsSearchComponent esSearchComponent;

    // Embedding模型（免费本地模型）
    private EmbeddingModel embeddingModel;

    /**
     * 初始化Embedding模型
     */
    @PostConstruct
    public void init() {
        // 使用反射加载Embedding模型（避免导入路径问题）
        try {
            // 尝试加载AllMiniLmL6V2EmbeddingModel
            Class<?> embeddingClass = Class.forName("dev.langchain4j.model.embedding.all.minilml6v2.AllMiniLmL6V2EmbeddingModel");
            embeddingModel = (EmbeddingModel) embeddingClass.getDeclaredConstructor().newInstance();
            logger.info("Embedding模型初始化完成（使用反射加载）");
        } catch (ClassNotFoundException e) {
            // 如果找不到类，尝试其他路径
            try {
                Class<?> embeddingClass = Class.forName("dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel");
                embeddingModel = (EmbeddingModel) embeddingClass.getDeclaredConstructor().newInstance();
                logger.info("Embedding模型初始化完成（使用反射加载，路径2）");
            } catch (Exception e2) {
                logger.error("无法加载Embedding模型，使用关键词搜索降级方案", e2);
                embeddingModel = null;
            }
        } catch (Exception e) {
            logger.error("Embedding模型初始化失败，使用关键词搜索降级方案", e);
            embeddingModel = null;
        }
    }

    /**
     * 发送消息并获取AI回复（无历史记录版本）
     * 
     * 实现流程：
     * 1. 索引检查：检查ES向量索引是否存在
     * 2. 向量检索：使用向量检索获取相关视频
     * 3. Prompt构造：将检索结果作为上下文注入Prompt
     * 4. 大模型生成：调用LangChain4j生成回复
     */
    public String chat(String userId, String userMessage) {
        try {
            logger.info("用户 {} 发送消息: {}", userId, userMessage);

            // 构建消息列表（不保存历史，每次都是新对话）
            List<ChatMessage> messages = new ArrayList<>();
            
            // 添加系统提示词
            String systemPrompt = buildSystemPrompt();
            messages.add(SystemMessage.from(systemPrompt));

            // 使用向量检索获取相关视频（如果失败则降级到关键词搜索）
            List<VideoInfo> videos = vectorSearchVideos(userMessage, 5);
            
            if (!videos.isEmpty()) {
                // 构建包含视频信息的上下文
                String videoContext = buildVideoContextFromVideoInfo(videos, userMessage);
                String enhancedMessage = userMessage + "\n\n" + videoContext;
                messages.add(UserMessage.from(enhancedMessage));
            } else {
                // 降级方案：使用关键词搜索
                String keyword = extractKeyword(userMessage);
                List<VideoKnowledgeService.VideoKnowledgeItem> videoItems = 
                        videoKnowledgeService.searchVideos(keyword, 5);
                if (!videoItems.isEmpty()) {
                    String videoContext = buildVideoContext(videoItems, keyword);
                    String enhancedMessage = userMessage + "\n\n" + videoContext;
                    messages.add(UserMessage.from(enhancedMessage));
                } else {
                    messages.add(UserMessage.from(userMessage));
                }
            }

            // 同步调用AI模型
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            String aiResponse = response.content().text();
            
            logger.info("AI 回复: {}", aiResponse);
            return aiResponse;

        } catch (Exception e) {
            logger.error("AI聊天失败，用户: {}, 消息: {}", userId, userMessage, e);
            return "抱歉，我遇到了一些问题。请稍后再试。";
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个视频网站的智能助手，名叫小易。\n\n");
        prompt.append("你的职责：\n");
        prompt.append("1. 帮助用户发现和推荐视频内容\n");
        prompt.append("2. 解答用户关于视频的问题\n");
        prompt.append("3. 提供友好、专业的服务\n\n");
        
        prompt.append("当前网站情况：\n");
        prompt.append(videoKnowledgeService.getKnowledgeBaseSummary());
        prompt.append("\n");
        
        prompt.append("回复要求：\n");
        prompt.append("- 每次回复都要有变化，避免重复相同的话\n");
        prompt.append("- 简洁明了，控制在100字以内\n");
        prompt.append("- 友好热情，适当使用表情符号\n");
        prompt.append("- **使用 Markdown 格式**回复，包括：\n");
        prompt.append("  * 使用 **粗体** 强调重点\n");
        prompt.append("  * 使用数字列表（1. 2. 3.）展示视频\n");
        prompt.append("  * 使用 `代码` 标记关键词\n");
        prompt.append("- 当用户询问视频推荐时，基于提供的视频信息进行推荐\n");
        prompt.append("- 根据对话上下文，给出个性化的回复\n");
        prompt.append("- 如果不确定，诚实告知并建议用户尝试其他关键词\n");
        
        return prompt.toString();
    }

    /**
     * 构建视频上下文信息
     */
    private String buildVideoContext(List<VideoKnowledgeService.VideoKnowledgeItem> videos, 
                                     String keyword) {
        StringBuilder context = new StringBuilder();
        context.append("【系统提供的视频信息】\n");
        context.append("关键词：").append(keyword).append("\n");
        context.append("找到 ").append(videos.size()).append(" 个相关视频：\n\n");
        
        for (int i = 0; i < videos.size(); i++) {
            VideoKnowledgeService.VideoKnowledgeItem video = videos.get(i);
            context.append(i + 1).append(". ").append(video.getFullText()).append("\n");
        }
        
        context.append("\n请基于以上视频信息，为用户推荐合适的视频。");
        return context.toString();
    }

    /**
     * 从用户消息中提取关键词
     */
    private String extractKeyword(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        
        // 移除常见的无意义词汇
        String keyword = message
                .replaceAll("推荐|视频|给我|看看|找找|搜索|一下|一些|几个|帮我|我想|想看|相关|有关|关于", "")
                .trim();
        
        // 如果提取后为空，返回原始消息
        if (keyword.isEmpty()) {
            keyword = message;
        }
        
        return keyword;
    }

    // ==================== 向量检索相关方法 ====================

    /**
     * 生成向量
     */
    public float[] generateEmbedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return new float[384];
            }
            
            // 如果Embedding模型未初始化，返回空向量
            if (embeddingModel == null) {
                logger.warn("Embedding模型未初始化，返回空向量");
                return new float[384];
            }
            
            Embedding embedding = embeddingModel.embed(text).content();
            // 将Embedding转换为float数组
            List<Float> vectorList = embedding.vectorAsList();
            float[] vectorArray = new float[vectorList.size()];
            for (int i = 0; i < vectorList.size(); i++) {
                vectorArray[i] = vectorList.get(i);
            }
            return vectorArray;
        } catch (Exception e) {
            logger.error("生成向量失败: text={}", text, e);
            return new float[384];
        }
    }

    /**
     * 向量检索视频
     */
    public List<VideoInfo> vectorSearchVideos(String query, int topK) {
        try {
            // 如果Embedding模型未初始化，使用关键词搜索降级方案
            if (embeddingModel == null) {
                logger.warn("Embedding模型未初始化，使用关键词搜索降级方案: query={}", query);
                return Collections.emptyList();
            }
            
            // 1. 检查索引
            if (!esSearchComponent.checkAndCreateVectorIndex()) {
                logger.warn("向量索引检查失败，使用关键词搜索");
                return Collections.emptyList();
            }
            
            // 2. 生成查询向量
            float[] queryVector = generateEmbedding(query);
            
            // 3. ES向量检索
            return esSearchComponent.vectorSearch(query, queryVector, topK);
            
        } catch (Exception e) {
            logger.error("向量检索失败: query={}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建视频上下文（从VideoInfo）
     */
    private String buildVideoContextFromVideoInfo(List<VideoInfo> videos, String keyword) {
        StringBuilder context = new StringBuilder();
        context.append("【系统提供的相关视频信息】\n");
        context.append("关键词：").append(keyword).append("\n");
        context.append("找到 ").append(videos.size()).append(" 个相关视频：\n\n");
        
        for (int i = 0; i < videos.size(); i++) {
            VideoInfo video = videos.get(i);
            context.append(i + 1).append(". ");
            context.append("标题：").append(video.getVideoName()).append("\n");
            if (video.getTags() != null && !video.getTags().isEmpty()) {
                context.append("   标签：").append(video.getTags()).append("\n");
            }
            if (video.getIntroduction() != null && !video.getIntroduction().isEmpty()) {
                context.append("   简介：").append(video.getIntroduction()).append("\n");
            }
            context.append("\n");
        }
        
        context.append("请基于以上视频信息，为用户推荐合适的视频。");
        return context.toString();
    }

    /**
     * 为视频生成向量块（合理分块）
     */
    public List<VectorBlock> generateVideoBlocks(VideoInfo video) {
        List<VectorBlock> blocks = new ArrayList<>();
        
        // 1. 标题块（权重最高）
        if (video.getVideoName() != null && !video.getVideoName().trim().isEmpty()) {
            float[] titleVector = generateEmbedding(video.getVideoName());
            blocks.add(VectorBlock.buildTitleBlock(video.getVideoId(), video.getVideoName(), titleVector));
        }
        
        // 2. 标签块（权重中等）
        if (video.getTags() != null && !video.getTags().trim().isEmpty()) {
            float[] tagsVector = generateEmbedding(video.getTags());
            blocks.add(VectorBlock.buildTagsBlock(video.getVideoId(), video.getTags(), tagsVector));
        }
        
        // 3. 简介块（权重较低）
        if (video.getIntroduction() != null && !video.getIntroduction().trim().isEmpty()) {
            float[] introVector = generateEmbedding(video.getIntroduction());
            blocks.add(VectorBlock.buildIntroductionBlock(video.getVideoId(), video.getIntroduction(), introVector));
        }
        
        return blocks;
    }

    /**
     * 预索引视频（视频发布时调用）
     */
    @Async
    public void preIndexVideo(VideoInfo video) {
        try {
            logger.info("开始预索引视频, videoId={}", video.getVideoId());
            
            // 1. 生成向量块（合理分块）
            List<VectorBlock> blocks = generateVideoBlocks(video);
            
            if (blocks.isEmpty()) {
                logger.warn("视频没有可索引的内容, videoId={}", video.getVideoId());
                return;
            }
            
            // 2. 幂等删除：先删除旧向量（如果存在）
            esSearchComponent.deleteVideoBlocks(video.getVideoId());
            
            // 3. 写入ES（分块索引）
            for (VectorBlock block : blocks) {
                esSearchComponent.indexVectorBlock(block);
            }
            
            logger.info("视频预索引完成, videoId={}, blocks={}", video.getVideoId(), blocks.size());
            
        } catch (Exception e) {
            logger.error("视频预索引失败, videoId={}", video.getVideoId(), e);
            // 预索引失败不影响主流程，可以后续补偿
        }
    }
}

