package com.easylive.component;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.mappers.UserInfoMapper;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import com.easylive.entity.config.AppConfig;
import com.easylive.entity.dto.VideoInfoEsDto;
import com.easylive.entity.po.VideoInfo;
import com.easylive.exception.BusinessException;
import com.easylive.utils.CopyTools;
import com.easylive.utils.JsonUtils;
import com.easylive.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.easylive.entity.dto.VectorBlock;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class EsSearchComponent {
    private final AppConfig appConfig;

    private final RestHighLevelClient restHighLevelClient;

    private final UserInfoMapper userInfoMapper;

    /**
     * 应用启动时自动创建ES索引
     */
    @PostConstruct
    public void init() {
        try {
            log.info("开始初始化ES索引...");
            createIndex();
            log.info("ES索引初始化完成");
        } catch (Exception e) {
            log.error("ES索引初始化失败", e);
        }
    }

    //判断索引是否存在
    private Boolean isExistIndex() throws IOException {
        //就是创建一个请求对象，他的参数就是索引名称
        GetIndexRequest getIndexRequest = new GetIndexRequest(appConfig.getEsIndexVideoName());
        //这个方法的作用是判断es里面是否存在所指定的索引
        return restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
    }

    //我可以这样理解吗。。。。getindexrequest主要是用于检查索引的，getrequest是用于检查文档的。
    //创建索引
    public void createIndex() {
        try {
                if(isExistIndex()){
                    return;
                }
                //创建请求对象
                CreateIndexRequest request = new CreateIndexRequest(appConfig.getEsIndexVideoName());
                //将发送的json数据解析成，用于处理逗号分隔内容
                request.settings(
                        "{\"analysis\": {\n" +
                                "      \"analyzer\": {\n" +
                                "        \"comma\": {\n" +
                                "          \"type\": \"pattern\",\n" +
                                "          \"pattern\": \",\"\n" +
                                "        }\n" +
                                "      }\n" +
                                "    }}", XContentType.JSON);
                //创建映射关系。。建立索引的字段用于搜索，没有建立索引的字段用于展示。不用关联表，不用写sql语句。
                request.mapping(
                        "{\"properties\": {\n" +
                                "      \"videoId\":{\n" +
                                "        \"type\": \"text\",\n" +
                                "        \"index\": false\n" +
                                "      },\n" +
                                "      \"userId\":{\n" +
                                "        \"type\": \"text\",\n" +
                                "        \"index\": false\n" +
                                "         },\n" +
                                "      \"videoCover\":{\n" +
                                "        \"type\": \"text\",\n" +
                                "        \"index\": false\n" +
                                "      },\n" +
                                "      \"videoName\":{\n" +
                                "        \"type\": \"text\",\n" +
                                "        \"analyzer\": \"ik_max_word\"\n" +
                                "      },\n" +
                                "      \"tags\":{\n" +
                                "        \"type\": \"text\",\n" +
                                "        \"analyzer\": \"comma\"\n" +
                                "      },\n" +
                                "      \"playCount\":{\n" +
                                "        \"type\":\"integer\",\n" +
                                "        \"index\":false\n" +
                                "      },\n" +
                                "      \"danmuCount\":{\n" +
                                "        \"type\":\"integer\",\n" +
                                "        \"index\":false\n" +
                                "      },\n" +
                                "      \"collectCount\":{\n" +
                                "        \"type\":\"integer\",\n" +
                                "        \"index\":false\n" +
                                "      },\n" +
                                "      \"createTime\":{\n" +
                                "        \"type\":\"date\",\n" +
                                "        \"format\": \"yyyy-MM-dd HH:mm:ss\",\n" +
                                "        \"index\": false\n" +
                                "      }\n" +
                                " }}", XContentType.JSON);
            //通过elasticsearch客户端来创建响应。
            CreateIndexResponse createIndexResponse = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);

            if(!createIndexResponse.isAcknowledged()){
                throw new BusinessException("初始化es失败");
            }
        } catch (Exception e) {
            log.info("初始化es失败");
        }
    }

    //将视频信息保存到 Elasticsearch 搜索引擎中。
    public void saveDoc(VideoInfo videoInfo){
        try {
            // 检查索引是否存在，如果不存在则创建
            if (!isExistIndex()) {
                log.info("ES索引不存在，开始创建索引: {}", appConfig.getEsIndexVideoName());
                createIndex();
            }

            //如果这个视频在es中中存在，则更新
            if (docExist(videoInfo.getVideoId())) {
                updateDoc(videoInfo);
            }
            else{
                VideoInfoEsDto videoInfoEsDto = CopyTools.copy(videoInfo, VideoInfoEsDto.class);
                videoInfoEsDto.setCollectCount(0);
                videoInfoEsDto.setPlayCount(0);
                videoInfoEsDto.setDanmuCount(0);
                //创建索引名为**的请求
                IndexRequest request = new IndexRequest(appConfig.getEsIndexVideoName());
                //这行代码的作用是构建一个Elasticsearch索引请求，准备将视频信息存储到Elasticsearch中，以便后续可以进行全文搜索。
                request.id(videoInfo.getVideoId()).source(JsonUtils.convertObj2Json(videoInfoEsDto), XContentType.JSON);
                restHighLevelClient.index(request, RequestOptions.DEFAULT);
                log.info("视频保存到ES成功，videoId: {}, videoName: {}", videoInfo.getVideoId(), videoInfo.getVideoName());
            }

        }catch(Exception e){
            log.error("保存es文档失败，videoId: {}", videoInfo.getVideoId(), e);
        }

    }

    //通过videoId判断该视频信息是否存在
    private Boolean docExist(String id) throws IOException {
        GetRequest getIndexRequest = new GetRequest(appConfig.getEsIndexVideoName(),id);
        GetResponse getResponse = restHighLevelClient.get(getIndexRequest, RequestOptions.DEFAULT);

        return getResponse.isExists();
    }

    //更新es中指定的视频信息
    private void updateDoc(VideoInfo videoInfo) {
        try {
            // 检查文档是否存在
            if (!docExist(videoInfo.getVideoId())) {
                log.warn("ES中不存在该视频文档，跳过更新，videoId: {}", videoInfo.getVideoId());
                return;
            }

            // 构建需要更新的字段映射
            Map<String, Object> updateFields = new HashMap<>();

            // 只更新有值的字段，明确指定而不是用反射
            if (videoInfo.getVideoName() != null) {
                updateFields.put("videoName", videoInfo.getVideoName());
            }
            if (videoInfo.getVideoCover() != null) {
                updateFields.put("videoCover", videoInfo.getVideoCover());
            }
            if (videoInfo.getTags() != null) {
                updateFields.put("tags", videoInfo.getTags());
            }

            // 如果没有需要更新的字段，直接返回
            if (updateFields.isEmpty()) {
                return;
            }

            // 创建更新请求。我要更新索引中 ID 为 videoId 的那个文档
            UpdateRequest updateRequest = new UpdateRequest(
                    appConfig.getEsIndexVideoName(),
                    videoInfo.getVideoId()
            );

            // 将字段映射添加到更新请求中
            updateRequest.doc(updateFields);

            // 执行更新操作
            restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);

        } catch (Exception e) {
            log.error("更新视频到ES失败，videoId: {}", videoInfo.getVideoId(), e);
            throw new BusinessException("更新失败");
        }
    }


    //感觉这一块的重点是更新点赞数和播放数和弹幕数。。
    public void updateDocCount(String videoId, String fieldName, Integer count) {
        try {
            // 检查文档是否存在
            if (!docExist(videoId)) {
                log.debug("ES中不存在该视频文档，跳过计数更新，videoId: {}", videoId);
                return;
            }

            // 创建更新请求，指定索引名称和文档ID
            UpdateRequest updateRequest = new UpdateRequest(appConfig.getEsIndexVideoName(), videoId);

            // 构造脚本参数：将count值作为参数传递给脚本
            // 这样做是为了避免字符串拼接的安全问题和提高性能
            Map<String, Object> params = new HashMap<>();
            params.put("count", count);  // 将count变量的值存入params中，键名为"count"

            // 构造Painless脚本内容
            // ctx._source表示当前文档，fieldName是字段名，params.count是传入的参数值
            // 例如，如果fieldName是"viewCount"，count是1，那么实际执行的就是：
            // ctx._source.viewCount += 1
            String scriptSource = "ctx._source." + fieldName + " += params.count";

            // 创建脚本对象
            Script script = new Script(ScriptType.INLINE, "painless", scriptSource, params);

            // 设置脚本并执行更新
            updateRequest.script(script);
            restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("更新ES文档字段失败，videoId: {}, fieldName: {}, count: {}", videoId, fieldName, count, e);
            throw new BusinessException("更新失败");
        }
    }

    //删除es中指定的视频信息
    public void deleteDoc(String videoId) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest(appConfig.getEsIndexVideoName(), videoId);
            restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("删除ES文档失败，videoId: {}", videoId, e);
            // ES索引不存在或文档不存在都是正常情况，不应阻断整个删除流程
            // 仅记录日志，不抛出异常
        }
    }


    public PaginationResultVO<VideoInfo> search(Boolean highlight, String keyword, Integer orderType, Integer pageNo, Integer pageSize) {
        try {
            //获取指定的搜索类型
            SearchOrderTypeEnum searchOrderTypeEnum = SearchOrderTypeEnum.getByType(orderType);
            //用来构建搜索请求的各种参数，比如查询条件、排序规则、分页设置、高亮显示等
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            //关键字,,,通过videoName和tags字段进行搜索
            searchSourceBuilder.query(QueryBuilders.multiMatchQuery(keyword, "videoName", "tags"));

            if (highlight) {
                // 设置高亮显示 - 当搜索结果中的关键词会用<span class='highlight'>标签包围
                searchSourceBuilder.highlighter(
                        new HighlightBuilder()
                                .field("videoName")
                                .preTags("<span class='highlight'>")
                                .postTags("</span>")
                );
            }

            //排序
            if (orderType != null && searchOrderTypeEnum != null) {
                searchSourceBuilder.sort(searchOrderTypeEnum.getField(), SortOrder.DESC); // 按照指定的字段进行降序排序
            }else{
                searchSourceBuilder.sort("_score", SortOrder.DESC); // 通过相关度评分排序
            }

            pageNo = pageNo == null ? 1 : pageNo;  //页码
            pageSize = pageSize == null ? PageSize.SIZE20.getSize() : pageSize;  //每页数量
            searchSourceBuilder.size(pageSize);  // Elasticsearch 一次返回多少条记录
            searchSourceBuilder.from((pageNo - 1) * pageSize);   //从第几条记录开始返回

            //创建一个search请求去向，指定索引名称。
            SearchRequest searchRequest = new SearchRequest(appConfig.getEsIndexVideoName());

            //设置一下搜索条件
            searchRequest.source(searchSourceBuilder);

            // 执行查询
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            //处理查询结果
            //获取搜索结果
            SearchHits hits = searchResponse.getHits();
            //获取总记录数，，就是有多少条记录匹配成功的
            Integer totalCount = (int) hits.getTotalHits().value;

            List<VideoInfo> videoInfoList = new ArrayList<>();
            List<String> userIdList = new ArrayList<>();
            //遍历匹配成功的记录
            for (SearchHit hit : hits.getHits()) {
                //将 Elasticsearch 返回的 JSON 字符串转换为 VideoInfo 对象。
                VideoInfo videoInfo = JsonUtils.convertJson2Obj(hit.getSourceAsString(), VideoInfo.class);
                //只是在视频名称上起到了高亮，但是在标签上并没有
                if (hit.getHighlightFields().get("videoName") != null) {
                    videoInfo.setVideoName(hit.getHighlightFields().get("videoName").fragments()[0].string());
                }
                videoInfoList.add(videoInfo);
                userIdList.add(videoInfo.getUserId());
            }

            UserInfoQuery userInfoQuery = new UserInfoQuery();
            userInfoQuery.setUserIdList(userIdList);
            List<UserInfo> userInfoList = userInfoMapper.selectList(userInfoQuery);
            Map<String, UserInfo> userInfoMap = new HashMap<>();
            for (UserInfo userInfo : userInfoList) {
                userInfoMap.put(userInfo.getUserId(), userInfo);
            }

            //填充用户信息
            for (VideoInfo videoInfo : videoInfoList) {
                UserInfo userInfo = userInfoMap.get(videoInfo.getUserId());
                System.out.println("userInfo123:"+userInfo);
                if (userInfo != null) {
                    videoInfo.setNickName(userInfo.getNickName());
                }
            }

            //构建分页结果
            SimplePage page = new SimplePage(pageNo, totalCount, pageSize);
            PaginationResultVO<VideoInfo> result = new PaginationResultVO(totalCount, page.getPageSize(), page.getPageNo(), page.getPageTotal(), videoInfoList);
            System.out.println("videoInfoList:"+videoInfoList);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询视频到es失败", e);
            throw new BusinessException("查询失败");
        }
    }

    // ==================== 向量检索相关方法 ====================

    /**
     * 向量索引名称（用于存储向量块）
     */
    private static final String VECTOR_INDEX_NAME = "video_vector_index";

    /**
     * 检查并创建向量索引（包含向量字段）
     */
    public boolean checkAndCreateVectorIndex() {
        try {
            GetIndexRequest request = new GetIndexRequest(VECTOR_INDEX_NAME);
            boolean exists = restHighLevelClient.indices().exists(request, RequestOptions.DEFAULT);
            
            if (!exists) {
                CreateIndexRequest createRequest = new CreateIndexRequest(VECTOR_INDEX_NAME);
                createRequest.mapping(buildVectorMapping(), XContentType.JSON);
                CreateIndexResponse response = restHighLevelClient.indices()
                    .create(createRequest, RequestOptions.DEFAULT);
                
                log.info("向量索引创建成功: {}", VECTOR_INDEX_NAME);
                return response.isAcknowledged();
            }
            return true;
        } catch (Exception e) {
            log.error("检查/创建向量索引失败", e);
            return false;
        }
    }

    /**
     * 构建向量索引映射
     */
    private String buildVectorMapping() {
        return "{\"properties\": {\n" +
                "      \"videoId\": {\n" +
                "        \"type\": \"keyword\",\n" +
                "        \"index\": false\n" +
                "      },\n" +
                "      \"blockType\": {\n" +
                "        \"type\": \"keyword\"\n" +
                "      },\n" +
                "      \"blockContent\": {\n" +
                "        \"type\": \"text\",\n" +
                "        \"analyzer\": \"ik_max_word\"\n" +
                "      },\n" +
                "      \"contentVector\": {\n" +
                "        \"type\": \"dense_vector\",\n" +
                "        \"dims\": 384,\n" +
                "        \"index\": true,\n" +
                "        \"similarity\": \"cosine\"\n" +
                "      },\n" +
                "      \"blockWeight\": {\n" +
                "        \"type\": \"integer\"\n" +
                "      }\n" +
                "    }}";
    }

    /**
     * 索引向量块（幂等删除后插入）
     */
    public void indexVectorBlock(VectorBlock block) {
        try {
            // 幂等删除：先删除该视频该类型的所有块
            deleteVideoBlocksByType(block.getVideoId(), block.getBlockType());
            
            // 构建文档ID：videoId_blockType
            String docId = block.getVideoId() + "_" + block.getBlockType();
            
            Map<String, Object> source = new HashMap<>();
            source.put("videoId", block.getVideoId());
            source.put("blockType", block.getBlockType());
            source.put("blockContent", block.getBlockContent());
            source.put("contentVector", block.getContentVector());
            source.put("blockWeight", block.getBlockWeight());
            
            IndexRequest request = new IndexRequest(VECTOR_INDEX_NAME);
            request.id(docId).source(JsonUtils.convertObj2Json(source), XContentType.JSON);
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
            
            log.debug("向量块索引成功: videoId={}, blockType={}", block.getVideoId(), block.getBlockType());
        } catch (Exception e) {
            log.error("索引向量块失败: videoId={}, blockType={}", block.getVideoId(), block.getBlockType(), e);
            throw new BusinessException("索引向量块失败");
        }
    }

    /**
     * 删除视频的所有向量块（幂等删除）
     */
    public void deleteVideoBlocks(String videoId) {
        try {
            // 删除该视频的所有块（title、tags、introduction）
            deleteVideoBlocksByType(videoId, "title");
            deleteVideoBlocksByType(videoId, "tags");
            deleteVideoBlocksByType(videoId, "introduction");
            log.debug("已删除视频所有向量块: videoId={}", videoId);
        } catch (Exception e) {
            log.error("删除视频向量块失败: videoId={}", videoId, e);
            throw new BusinessException("删除向量块失败");
        }
    }

    /**
     * 删除指定视频指定类型的向量块
     */
    private void deleteVideoBlocksByType(String videoId, String blockType) {
        try {
            String docId = videoId + "_" + blockType;
            DeleteRequest deleteRequest = new DeleteRequest(VECTOR_INDEX_NAME, docId);
            // 幂等删除：即使不存在也不会报错
            restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        } catch (org.elasticsearch.ElasticsearchStatusException e) {
            // 文档不存在，忽略（幂等性）
            if (e.status().getStatus() == 404) {
                log.debug("向量块不存在，忽略删除: videoId={}, blockType={}", videoId, blockType);
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.warn("删除向量块时出错（幂等忽略）: videoId={}, blockType={}", videoId, blockType, e);
        }
    }

    /**
     * 向量检索（分块检索，加权合并）
     */
    public List<VideoInfo> vectorSearch(String query, float[] queryVector, int topK) {
        try {
            // 1. 检查索引
            if (!checkAndCreateVectorIndex()) {
                log.warn("向量索引检查失败，返回空结果");
                return Collections.emptyList();
            }
            
            // 2. 对每个块类型分别检索
            Map<String, Double> videoScores = new HashMap<>();
            
            // 检索标题块（权重1.0）
            searchBlockAndMerge("title", queryVector, topK * 2, 1.0, videoScores);
            
            // 检索标签块（权重0.5）
            searchBlockAndMerge("tags", queryVector, topK * 2, 0.5, videoScores);
            
            // 检索简介块（权重0.3）
            searchBlockAndMerge("introduction", queryVector, topK * 2, 0.3, videoScores);
            
            // 3. 按加权分数排序，返回Top-K的videoId
            List<String> topVideoIds = videoScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            // 4. 从主索引获取完整视频信息
            return getVideosByIds(topVideoIds);
            
        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 检索指定类型的块并合并分数
     */
    private void searchBlockAndMerge(String blockType, float[] queryVector, int size, 
                                     double weight, Map<String, Double> videoScores) {
        try {
            SearchRequest searchRequest = new SearchRequest(VECTOR_INDEX_NAME);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // 构建向量检索查询
            ScriptScoreQueryBuilder scriptQuery = QueryBuilders.scriptScoreQuery(
                QueryBuilders.termQuery("blockType", blockType),
                new Script(ScriptType.INLINE, "painless",
                    "cosineSimilarity(params.queryVector, 'contentVector') + 1.0",
                    Collections.singletonMap("queryVector", queryVector)
                )
            );
            
            sourceBuilder.query(scriptQuery);
            sourceBuilder.size(size);
            sourceBuilder.sort("_score", SortOrder.DESC);
            
            searchRequest.source(sourceBuilder);
            
            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            
            // 合并分数
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                String videoId = (String) source.get("videoId");
                double score = hit.getScore() * weight;  // 加权
                videoScores.merge(videoId, score, Double::sum);
            }
            
        } catch (Exception e) {
            log.warn("检索块失败: blockType={}", blockType, e);
        }
    }

    /**
     * 根据videoId列表获取视频信息
     */
    private List<VideoInfo> getVideosByIds(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            SearchRequest searchRequest = new SearchRequest(appConfig.getEsIndexVideoName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // 使用terms查询
            sourceBuilder.query(QueryBuilders.termsQuery("videoId", videoIds));
            sourceBuilder.size(videoIds.size());
            
            searchRequest.source(sourceBuilder);
            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            
            List<VideoInfo> videoList = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                VideoInfo videoInfo = JsonUtils.convertJson2Obj(hit.getSourceAsString(), VideoInfo.class);
                videoList.add(videoInfo);
            }
            
            return videoList;
        } catch (Exception e) {
            log.error("获取视频信息失败", e);
            return Collections.emptyList();
        }
    }

}
