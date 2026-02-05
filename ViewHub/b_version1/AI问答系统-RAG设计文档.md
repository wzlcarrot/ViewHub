# AI问答系统 - RAG设计文档（基于ES向量检索）

## 一、系统概述

### 1.1 业务场景
AI问答系统需要支持：
- **智能问答**：用户提问，AI基于视频知识库生成个性化回复
- **视频推荐**：根据用户问题推荐相关视频
- **语义理解**：理解用户意图，而非简单的关键词匹配

### 1.2 核心需求
1. **向量检索**：使用语义相似度检索相关视频，而非关键词匹配
2. **流式生成**：实时返回AI回复，提升用户体验
3. **索引管理**：自动检查和管理ES索引
4. **上下文注入**：将检索结果注入Prompt，提升回答准确性

---

## 二、系统架构设计

### 2.1 整体架构图

```
用户提问
    ↓
┌─────────────────────────────────────────┐
│  AIChatController.chat()                │
│  - 接收用户消息                          │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  1. 索引检查                            │
│     - 检查ES索引是否存在                 │
│     - 不存在则创建索引（含向量字段）     │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  2. 向量检索                            │
│     - 用户问题 → Embedding模型 → 向量    │
│     - ES向量检索（dense_vector）         │
│     - 返回Top-K相关视频                  │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  3. Prompt构造                          │
│     - 系统提示词                         │
│     - 检索到的视频上下文                 │
│     - 用户问题                           │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  4. 大模型流式生成                      │
│     - LangChain4j流式API                │
│     - Server-Sent Events (SSE)          │
│     - 实时返回生成内容                   │
└─────────────────────────────────────────┘
```

### 2.2 技术栈
- **LangChain4j**: AI框架，支持流式生成
- **Elasticsearch**: 向量检索（dense_vector字段）
- **Embedding模型**: all-minilm-l6-v2（免费本地模型，384维）
- **大模型**: Ollama本地模型（免费）或OpenAI API（可选，需付费）

**免费方案说明**：
- ✅ **all-minilm-l6-v2**：完全免费，本地运行，无需API Key，384维向量
- ✅ **Ollama**：完全免费，本地运行，支持多种开源模型（llama2、mistral等）
- ⚠️ **OpenAI API**：可选，需付费，但效果更好

**推荐配置**：
- Embedding：all-minilm-l6-v2（免费）
- 大模型：Ollama + llama2（免费）
- 总成本：**0元**（完全免费方案）

---

## 三、核心模块设计

### 3.1 索引检查模块

#### 3.1.1 设计思路
在每次检索前检查ES索引是否存在，如果不存在则自动创建（包含向量字段）。

#### 3.1.2 ES索引结构

```json
{
  "mappings": {
    "properties": {
      "videoId": {
        "type": "keyword",
        "index": false
      },
      "videoName": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "tags": {
        "type": "text",
        "analyzer": "comma"
      },
      "introduction": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "contentVector": {
        "type": "dense_vector",
        "dims": 384,
        "index": true,
        "similarity": "cosine"
      },
      "playCount": {
        "type": "integer"
      },
      "likeCount": {
        "type": "integer"
      }
    }
  }
}
```

#### 3.1.3 实现逻辑

```java
public boolean checkAndCreateIndex() {
    try {
        // 1. 检查索引是否存在
        GetIndexRequest request = new GetIndexRequest(INDEX_NAME);
        boolean exists = restHighLevelClient.indices().exists(request, RequestOptions.DEFAULT);
        
        if (!exists) {
            // 2. 创建索引（包含向量字段）
            CreateIndexRequest createRequest = new CreateIndexRequest(INDEX_NAME);
            createRequest.mapping(buildVectorMapping(), XContentType.JSON);
            CreateIndexResponse response = restHighLevelClient.indices()
                .create(createRequest, RequestOptions.DEFAULT);
            
            return response.isAcknowledged();
        }
        return true;
    } catch (Exception e) {
        log.error("检查/创建索引失败", e);
        return false;
    }
}
```

**关键代码位置**：
- `EsSearchComponent.checkAndCreateIndex()` - 索引检查
- `EsSearchComponent.buildVectorMapping()` - 构建向量映射

---

### 3.2 向量检索模块

#### 3.2.1 设计思路
1. 使用Embedding模型将用户问题转换为向量
2. 使用ES的`dense_vector`字段进行向量检索
3. 返回Top-K相关视频（K=5）

#### 3.2.2 向量生成

```java
public float[] generateEmbedding(String text) {
    // 使用免费的本地Embedding模型（无需API Key）
    EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    Embedding embedding = embeddingModel.embed(text).content();
    return embedding.vectorAsArray();  // 返回384维向量
}
```

#### 3.2.3 ES向量检索

```java
public List<VideoInfo> vectorSearch(String query, int topK) {
    try {
        // 1. 生成查询向量
        float[] queryVector = generateEmbedding(query);
        
        // 2. 构建向量检索请求
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        // 3. 使用script_score进行向量相似度计算
        ScriptScoreQueryBuilder scriptQuery = QueryBuilders.scriptScoreQuery(
            QueryBuilders.matchAllQuery(),
            new Script(ScriptType.INLINE, "painless",
                "cosineSimilarity(params.queryVector, 'contentVector') + 1.0",
                Collections.singletonMap("queryVector", queryVector)
            )
        );
        
        sourceBuilder.query(scriptQuery);
        sourceBuilder.size(topK);
        sourceBuilder.sort("_score", SortOrder.DESC);
        
        searchRequest.source(sourceBuilder);
        
        // 4. 执行检索
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        
        // 5. 解析结果
        return parseSearchResponse(response);
        
    } catch (Exception e) {
        log.error("向量检索失败", e);
        return Collections.emptyList();
    }
}
```

**关键代码位置**：
- `AIChatService.generateEmbedding()` - 生成向量
- `EsSearchComponent.vectorSearch()` - ES向量检索

#### 3.2.4 合理分块策略

**设计思路**：
将视频信息分为多个块（标题、标签、简介），分别生成向量，提升检索精度。

**为什么需要分块**：
- 视频信息包含多个部分（标题、标签、简介），语义不同
- 整体生成向量可能丢失细节信息
- 分块后可以更精确地匹配用户意图

**分块方案**：
1. **标题块**：视频标题（权重最高，100分）
2. **标签块**：视频标签（权重中等，50分）
3. **简介块**：视频简介（权重较低，30分）

**ES索引结构（支持分块）**：
```json
{
  "mappings": {
    "properties": {
      "videoId": {
        "type": "keyword",
        "index": false
      },
      "blockType": {
        "type": "keyword"
      },
      "blockContent": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "contentVector": {
        "type": "dense_vector",
        "dims": 384,
        "index": true,
        "similarity": "cosine"
      },
      "blockWeight": {
        "type": "integer"
      }
    }
  }
}
```

**实现逻辑**：
```java
/**
 * 为视频生成多个向量块
 */
public void indexVideoWithBlocks(VideoInfo video) {
    String videoId = video.getVideoId();
    List<VectorBlock> blocks = new ArrayList<>();
    
    // 1. 标题块（权重最高）
    if (video.getVideoName() != null && !video.getVideoName().isEmpty()) {
        float[] titleVector = generateEmbedding(video.getVideoName());
        blocks.add(new VectorBlock(videoId, "title", video.getVideoName(), titleVector, 100));
    }
    
    // 2. 标签块（权重中等）
    if (video.getTags() != null && !video.getTags().isEmpty()) {
        float[] tagsVector = generateEmbedding(video.getTags());
        blocks.add(new VectorBlock(videoId, "tags", video.getTags(), tagsVector, 50));
    }
    
    // 3. 简介块（权重较低）
    if (video.getIntroduction() != null && !video.getIntroduction().isEmpty()) {
        float[] introVector = generateEmbedding(video.getIntroduction());
        blocks.add(new VectorBlock(videoId, "introduction", video.getIntroduction(), introVector, 30));
    }
    
    // 4. 分别写入ES
    for (VectorBlock block : blocks) {
        esSearchComponent.indexVectorBlock(block);
    }
}

/**
 * 分块检索
 */
public List<VideoInfo> vectorSearchWithBlocks(String query, int topK) {
    // 1. 生成查询向量
    float[] queryVector = generateEmbedding(query);
    
    // 2. 对每个块分别检索
    Map<String, Double> videoScores = new HashMap<>();
    
    // 检索标题块
    List<SearchResult> titleResults = searchBlock("title", queryVector, topK * 2);
    titleResults.forEach(r -> {
        double weightedScore = r.getScore() * 1.0;  // 标题权重最高
        videoScores.merge(r.getVideoId(), weightedScore, Double::sum);
    });
    
    // 检索标签块
    List<SearchResult> tagsResults = searchBlock("tags", queryVector, topK * 2);
    tagsResults.forEach(r -> {
        double weightedScore = r.getScore() * 0.5;  // 标签权重中等
        videoScores.merge(r.getVideoId(), weightedScore, Double::sum);
    });
    
    // 检索简介块
    List<SearchResult> introResults = searchBlock("introduction", queryVector, topK * 2);
    introResults.forEach(r -> {
        double weightedScore = r.getScore() * 0.3;  // 简介权重较低
        videoScores.merge(r.getVideoId(), weightedScore, Double::sum);
    });
    
    // 3. 按加权分数排序，返回Top-K
    return videoScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topK)
        .map(e -> getVideoById(e.getKey()))
        .collect(Collectors.toList());
}
```

**优势**：
- **检索精度提升**：不同块的内容语义不同，分块检索更精确
- **权重控制**：可以为不同块设置不同权重（标题>标签>简介）
- **灵活性**：可以根据用户问题类型调整检索策略

**关键代码位置**：
- `EsSearchComponent.indexVideoWithBlocks()` - 分块索引
- `EsSearchComponent.vectorSearchWithBlocks()` - 分块检索

---

### 3.3 Prompt构造模块

#### 3.3.1 设计思路
将检索到的视频信息注入Prompt，让AI基于实际数据生成回复。

#### 3.3.2 Prompt模板

```
你是一个视频网站的智能助手，名叫小易。

你的职责：
1. 帮助用户发现和推荐视频内容
2. 解答用户关于视频的问题
3. 提供友好、专业的服务

当前网站情况：
{knowledgeBaseSummary}

【系统提供的相关视频信息】
关键词：{keyword}
找到 {count} 个相关视频：

{videoContext}

回复要求：
- 简洁明了，控制在100字以内
- 友好热情，适当使用表情符号
- 使用 Markdown 格式回复
- 基于提供的视频信息进行推荐
- 如果不确定，诚实告知并建议用户尝试其他关键词

用户问题：{userMessage}
```

#### 3.3.3 实现逻辑

```java
private String buildPrompt(String userMessage, List<VideoInfo> videos, String keyword) {
    StringBuilder prompt = new StringBuilder();
    
    // 1. 系统提示词
    prompt.append("你是一个视频网站的智能助手，名叫小易。\n\n");
    prompt.append("你的职责：\n");
    prompt.append("1. 帮助用户发现和推荐视频内容\n");
    prompt.append("2. 解答用户关于视频的问题\n");
    prompt.append("3. 提供友好、专业的服务\n\n");
    
    // 2. 知识库摘要
    prompt.append("当前网站情况：\n");
    prompt.append(videoKnowledgeService.getKnowledgeBaseSummary());
    prompt.append("\n");
    
    // 3. 检索到的视频上下文
    if (!videos.isEmpty()) {
        prompt.append("【系统提供的相关视频信息】\n");
        prompt.append("关键词：").append(keyword).append("\n");
        prompt.append("找到 ").append(videos.size()).append(" 个相关视频：\n\n");
        
        for (int i = 0; i < videos.size(); i++) {
            VideoInfo video = videos.get(i);
            prompt.append(i + 1).append(". ")
                  .append("标题：").append(video.getVideoName()).append("\n");
            if (video.getTags() != null) {
                prompt.append("   标签：").append(video.getTags()).append("\n");
            }
            if (video.getIntroduction() != null) {
                prompt.append("   简介：").append(video.getIntroduction()).append("\n");
            }
            prompt.append("\n");
        }
    }
    
    // 4. 回复要求
    prompt.append("回复要求：\n");
    prompt.append("- 简洁明了，控制在100字以内\n");
    prompt.append("- 友好热情，适当使用表情符号\n");
    prompt.append("- 使用 Markdown 格式回复\n");
    prompt.append("- 基于提供的视频信息进行推荐\n\n");
    
    // 5. 用户问题
    prompt.append("用户问题：").append(userMessage);
    
    return prompt.toString();
}
```

**关键代码位置**：
- `AIChatService.buildPrompt()` - 构建Prompt

---

### 3.4 流式生成模块

#### 3.4.1 设计思路
使用LangChain4j的流式API，通过Server-Sent Events (SSE)实时返回生成内容。

#### 3.4.2 流式API调用

**方案一：使用OpenAI API（需付费）**
```java
// 配置OpenAI模型
ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-3.5-turbo")
    .build();
```

**方案二：使用Ollama本地模型（免费）**
```java
// 配置Ollama本地模型（完全免费）
ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")  // Ollama默认地址
    .modelName("llama2")  // 或其他开源模型
    .build();
```

**流式生成实现**：
```java
public void streamChat(String userMessage, SseEmitter emitter) {
    try {
        // 1. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt()));
        messages.add(UserMessage.from(userMessage));
        
        // 2. 使用流式API（支持OpenAI和Ollama）
        StreamingChatLanguageModel streamingModel = 
            StreamingChatLanguageModel.from(chatLanguageModel);
        
        // 3. 流式生成
        streamingModel.generateStream(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                try {
                    // 发送每个token到前端
                    emitter.send(SseEmitter.event()
                        .data(token)
                        .name("message"));
                } catch (IOException e) {
                    log.error("发送SSE消息失败", e);
                }
            }
            
            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    emitter.send(SseEmitter.event()
                        .data("[DONE]")
                        .name("done"));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
            
            @Override
            public void onError(Throwable error) {
                log.error("流式生成失败", error);
                emitter.completeWithError(error);
            }
        });
        
    } catch (Exception e) {
        log.error("流式聊天失败", e);
        emitter.completeWithError(e);
    }
}
```

#### 3.4.3 Controller实现

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestParam String message, HttpSession session) {
    String userId = getUserIdFromSession(session);
    
    // 创建SSE连接（超时时间60秒）
    SseEmitter emitter = new SseEmitter(60000L);
    
    // 异步处理
    CompletableFuture.runAsync(() -> {
        try {
            // 1. 索引检查
            if (!esSearchComponent.checkAndCreateIndex()) {
                emitter.send(SseEmitter.event()
                    .data("系统错误：索引初始化失败")
                    .name("error"));
                emitter.complete();
                return;
            }
            
            // 2. 向量检索
            List<VideoInfo> videos = esSearchComponent.vectorSearch(message, 5);
            
            // 3. 构建Prompt
            String prompt = aiChatService.buildPrompt(message, videos, message);
            
            // 4. 流式生成
            aiChatService.streamChat(prompt, emitter);
            
        } catch (Exception e) {
            log.error("流式聊天处理失败", e);
            emitter.completeWithError(e);
        }
    }, aiExecutorService);
    
    return emitter;
}
```

**关键代码位置**：
- `AIChatController.streamChat()` - 流式聊天接口
- `AIChatService.streamChat()` - 流式生成逻辑

---

## 四、完整流程

### 4.1 数据流程

```
用户提问："推荐一些编程教程"
    ↓
┌─────────────────────────────────────────┐
│  1. 索引检查                            │
│     - 检查ES索引是否存在                 │
│     - 不存在则创建（含contentVector字段）│
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  2. 向量生成（预索引已完成）              │
│     - "推荐一些编程教程"                 │
│     - Embedding模型 → [0.1, 0.2, ...]  │
│     - 384维向量（all-minilm-l6-v2免费模型）│
│     - 注意：视频向量已在发布时预生成      │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  3. ES向量检索（合理分块）               │
│     - 对标题、标签、简介分别检索         │
│     - cosineSimilarity计算相似度        │
│     - 加权合并结果（标题权重最高）        │
│     - 返回Top-5相关视频                  │
│     - 结果：Java教程、Python教程等       │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  4. Prompt构造                          │
│     - 系统提示词                         │
│     - 检索到的5个视频信息                │
│     - 用户问题                           │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  5. 流式生成                            │
│     - LangChain4j流式API                │
│     - SSE实时推送                        │
│     - "我为您推荐以下编程教程..."        │
└─────────────────────────────────────────┘
```

### 4.2 时序图

```
用户          Controller        AIChatService      EsSearchComponent    大模型
 │                │                    │                    │            (Ollama/OpenAI)
 │--提问--------->│                    │                    │              │
 │                │--索引检查--------->│                    │              │
 │                │                    │--检查索引--------->│              │
 │                │                    │<--索引存在---------│              │
 │                │--向量检索--------->│                    │              │
 │                │                    │--生成向量(免费)---->│              │
 │                │                    │--ES检索----------->│              │
 │                │                    │<--Top-5视频--------│              │
 │                │--构建Prompt------->│                    │              │
 │                │--流式生成--------->│                    │              │
 │                │                    │                    │              │
 │<--token1-------│                    │                    │--流式API---->│
 │<--token2-------│                    │                    │<--token1------│
 │<--token3-------│                    │                    │<--token2------│
 │<--完成---------│                    │                    │<--完成--------│
```

---

## 五、关键技术点

### 5.1 ES向量检索

#### 5.1.1 dense_vector字段
- **类型**：`dense_vector`
- **维度**：384（all-minilm-l6-v2免费本地模型）
- **相似度算法**：cosine（余弦相似度）
- **索引**：启用（`index: true`）

#### 5.1.2 向量检索查询

```java
// 使用script_score进行向量相似度计算
ScriptScoreQueryBuilder scriptQuery = QueryBuilders.scriptScoreQuery(
    QueryBuilders.matchAllQuery(),
    new Script(ScriptType.INLINE, "painless",
        "cosineSimilarity(params.queryVector, 'contentVector') + 1.0",
        Collections.singletonMap("queryVector", queryVector)
    )
);
```

**说明**：
- `cosineSimilarity`返回-1到1之间的值
- 加1.0是为了让分数变为0到2之间（ES要求分数非负）

### 5.2 流式生成

#### 5.2.1 Server-Sent Events (SSE)
- **协议**：HTTP长连接
- **格式**：`data: {content}\n\n`
- **优势**：服务器主动推送，实时性好

#### 5.2.2 LangChain4j流式API

```java
StreamingChatLanguageModel streamingModel = 
    StreamingChatLanguageModel.from(chatLanguageModel);

streamingModel.generateStream(messages, new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onNext(String token) {
        // 每个token都会触发此方法
    }
    
    @Override
    public void onComplete(Response<AiMessage> response) {
        // 生成完成
    }
    
    @Override
    public void onError(Throwable error) {
        // 生成失败
    }
});
```

### 5.3 索引管理

#### 5.3.1 自动创建索引
- 应用启动时检查索引
- 如果不存在则自动创建
- 包含向量字段映射

#### 5.3.2 预索引策略

**设计思路**：
视频发布时预生成向量并写入ES，避免首次查询时等待，减少首次提问等待时间。

**为什么需要预索引**：
- **首次查询慢**：如果查询时才生成向量，首次提问需要等待向量生成（2-3秒）
- **用户体验差**：用户等待时间长，体验不好
- **系统压力大**：查询时生成向量会增加系统压力

**预索引流程**：
```
视频发布
    ↓
┌─────────────────────────────────────────┐
│  1. 保存视频到数据库                    │
│     - 同步操作，立即返回                 │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  2. 异步预索引（不阻塞主流程）           │
│     - 生成向量（标题、标签、简介）        │
│     - 写入ES（分块索引）                 │
│     - 记录日志                           │
└─────────────────────────────────────────┘
```

**实现逻辑**：
```java
/**
 * 视频发布时预索引
 */
@Async
public void preIndexVideo(VideoInfo video) {
    try {
        log.info("开始预索引视频, videoId={}", video.getVideoId());
        
        // 1. 生成向量块（合理分块）
        List<VectorBlock> blocks = generateVideoBlocks(video);
        
        // 2. 幂等删除：先删除旧向量（如果存在）
        esSearchComponent.deleteVideoBlocks(video.getVideoId());
        
        // 3. 写入ES（分块索引）
        for (VectorBlock block : blocks) {
            esSearchComponent.indexVectorBlock(block);
        }
        
        log.info("视频预索引完成, videoId={}, blocks={}", video.getVideoId(), blocks.size());
        
    } catch (Exception e) {
        log.error("视频预索引失败, videoId={}", video.getVideoId(), e);
        // 预索引失败不影响主流程，可以后续补偿
    }
}

/**
 * 视频发布接口
 */
public void publishVideo(VideoInfo video) {
    // 1. 保存视频到数据库（同步）
    videoInfoMapper.insert(video);
    
    // 2. 异步预索引（不阻塞主流程）
    preIndexVideo(video);
    
    // 3. 立即返回（用户不需要等待向量生成）
}
```

**时间对比**：

```
❌ 没有预索引（查询时生成）：
用户提问 → 生成向量(2秒) → 写入ES(0.5秒) → 检索(0.1秒) = 总耗时2.6秒

✅ 有预索引（发布时生成）：
用户提问 → 检索(0.1秒) = 总耗时0.1秒

提升：2.6秒 → 0.1秒，提升96%！
```

**优势**：
- **响应速度快**：查询时直接检索，无需等待向量生成
- **用户体验好**：首次提问也能快速响应
- **系统压力小**：向量生成分散到发布时，避免查询高峰期压力

**关键代码位置**：
- `VideoInfoService.preIndexVideo()` - 预索引逻辑
- `VideoInfoService.publishVideo()` - 视频发布（触发预索引）

#### 5.3.3 幂等删除保持单一版本

**设计思路**：
视频更新时，先删除旧向量再插入新向量，保证索引中只有单一版本，避免重复数据。

**为什么需要幂等删除**：
- **避免重复数据**：如果直接插入，更新多次可能产生多个版本的向量
- **保证数据一致性**：索引中只保留最新版本
- **简化检索逻辑**：不需要去重，检索结果更准确

**实现逻辑**：
```java
/**
 * 更新视频向量（幂等删除）
 */
public void updateVideoVector(VideoInfo video) {
    String videoId = video.getVideoId();
    
    try {
        // 1. 幂等删除：先删除旧向量（即使不存在也不会报错）
        esSearchComponent.deleteVideoBlocks(videoId);
        log.debug("已删除旧向量, videoId={}", videoId);
        
        // 2. 生成新向量块
        List<VectorBlock> blocks = generateVideoBlocks(video);
        
        // 3. 插入新向量
        for (VectorBlock block : blocks) {
            esSearchComponent.indexVectorBlock(block);
        }
        
        log.info("视频向量更新完成, videoId={}, blocks={}", videoId, blocks.size());
        
    } catch (Exception e) {
        log.error("更新视频向量失败, videoId={}", videoId, e);
        throw new BusinessException("更新视频向量失败");
    }
}
```

**幂等性说明**：
- **幂等操作**：多次执行删除操作，结果一致（不存在时删除也不会报错）
- **单一版本**：更新后索引中只有最新版本的向量，不会重复

**优势**：
- **数据一致性**：保证索引中只有最新版本
- **检索准确**：不会因为旧数据影响检索结果
- **简化逻辑**：不需要复杂的去重逻辑

**关键代码位置**：
- `EsSearchComponent.deleteVideoBlocks()` - 幂等删除
- `VideoInfoService.updateVideoVector()` - 更新向量（幂等删除+插入）

---

## 六、性能优化

### 6.1 向量生成优化
- **预索引**：视频发布时预生成向量并写入ES，减少首次提问等待时间
- **合理分块**：标题、标签、简介分别生成向量，提升检索精度
- **缓存**：相同文本的向量结果缓存（Redis）
- **批量生成**：批量处理视频向量，减少API调用
- **异步处理**：视频发布时异步生成向量，不阻塞主流程

### 6.2 ES检索优化
- **索引优化**：使用合适的相似度算法（cosine）
- **分片策略**：根据数据量设置合适的分片数
- **缓存策略**：热门查询结果缓存

### 6.3 流式生成优化
- **超时控制**：设置合理的超时时间（60秒）
- **错误处理**：网络异常时优雅降级
- **连接管理**：及时关闭SSE连接

---

## 七、错误处理

### 7.1 索引不存在
- **处理**：自动创建索引
- **降级**：如果创建失败，使用关键词检索

### 7.2 向量生成失败
- **处理**：记录日志，使用关键词检索
- **降级**：回退到传统关键词搜索

### 7.3 ES检索失败
- **处理**：记录日志，返回空结果
- **降级**：使用内存知识库的关键词匹配

### 7.4 流式生成失败
- **处理**：关闭SSE连接，返回错误信息
- **降级**：使用同步生成，返回完整结果

---

## 八、监控与运维

### 8.1 关键指标
- **向量生成耗时**：平均耗时、P99耗时
- **ES检索耗时**：平均耗时、P99耗时
- **流式生成耗时**：首token延迟、总耗时
- **错误率**：向量生成失败率、ES检索失败率

### 8.2 日志记录
- **关键日志点**：
  - 索引检查/创建
  - 向量生成
  - ES检索（查询内容、结果数量）
  - Prompt构造
  - 流式生成（首token时间、总耗时）

### 8.3 告警机制
- **索引创建失败**：告警通知
- **ES连接失败**：告警通知
- **向量生成失败率过高**：告警通知
- **流式生成超时率过高**：告警通知

---

## 九、实现步骤

### 9.1 第一阶段：基础功能
1. ✅ 实现索引检查（检查ES索引是否存在）
2. ✅ 实现向量生成（使用Embedding模型）
3. ✅ 实现ES向量检索（dense_vector字段）
4. ✅ 实现合理分块（标题、标签、简介分别生成向量）
5. ✅ 实现预索引（视频发布时预生成向量）
6. ✅ 实现Prompt构造（注入检索结果）

### 9.2 第二阶段：流式生成
1. ✅ 实现流式API调用（LangChain4j）
2. ✅ 实现SSE推送（Server-Sent Events）
3. ✅ 实现错误处理和降级

### 9.3 第三阶段：优化完善
1. ⏳ 实现向量缓存（Redis）
2. ⏳ 实现批量向量生成
3. ⏳ 实现监控和告警

---

## 十、总结

### 10.1 架构优势
1. **语义理解**：向量检索比关键词匹配更准确
2. **实时体验**：流式生成提升用户体验
3. **可扩展性**：ES支持大规模向量检索
4. **可靠性**：完善的错误处理和降级机制

### 10.2 技术亮点
1. **ES向量检索**：使用dense_vector字段，支持大规模向量检索
2. **流式生成**：SSE实时推送，用户体验好
3. **索引管理**：自动检查创建，运维友好
4. **上下文注入**：检索结果注入Prompt，提升准确性

### 10.3 适用场景
- 视频平台的智能问答
- 内容推荐系统
- 知识库问答
- 其他需要语义检索的场景

---

## 附录：关键代码文件

### 核心文件清单

1. **AI聊天服务**：
   - `AIChatService.java` - AI聊天服务（向量生成、Prompt构造、流式生成）
   - `AIChatController.java` - AI聊天控制器（SSE接口）

2. **ES检索组件**：
   - `EsSearchComponent.java` - ES检索组件（索引检查、向量检索）

3. **知识库服务**：
   - `VideoKnowledgeService.java` - 视频知识库服务

4. **配置类**：
   - `LangChainConfig.java` - LangChain4j配置
   - `EsConfiguration.java` - Elasticsearch配置

### Maven依赖

```xml
<!-- LangChain4j 核心依赖 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.25.0</version>
</dependency>

<!-- LangChain4j 免费Embedding模型（本地模型，无需API Key） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    <version>0.25.0</version>
</dependency>

<!-- LangChain4j OpenAI 支持（可选，用于大模型，需付费） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.25.0</version>
</dependency>

<!-- LangChain4j Ollama 支持（免费本地大模型） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>0.25.0</version>
</dependency>
```

**说明**：
- `langchain4j-embeddings-all-minilm-l6-v2`：**免费**本地Embedding模型，无需API Key，384维
- `langchain4j-ollama`：**免费**本地大模型，需要本地安装Ollama服务
- `langchain4j-open-ai`：可选，OpenAI API（需付费）
- **推荐方案**：Embedding使用all-minilm-l6-v2（免费），大模型使用Ollama（免费）

---

**文档版本**：v1.0  
**最后更新**：2024年  
**维护人员**：开发团队

