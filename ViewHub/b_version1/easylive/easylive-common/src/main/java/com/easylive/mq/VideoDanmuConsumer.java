package com.easylive.mq;

import com.easylive.component.EsSearchComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.VideoDanmuTaskDTO;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.VideoDanmu;
import com.easylive.mappers.VideoDanmuMapper;
import com.easylive.mappers.VideoInfoMapper;
import com.easylive.redis.BloomFilterComponent;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 弹幕任务消息消费者
 * 
 * 职责：监听 RabbitMQ 消息，批量处理弹幕写入MySQL
 * 
 * 工作流程：
 * 1. 监听 MQ 中的弹幕任务消息
 * 2. 批量收集弹幕消息（按fileId分组）
 * 3. 批量写入MySQL（批量INSERT）
 * 4. 加入布隆过滤器
 * 5. 更新视频弹幕数量
 * 6. 更新ES弹幕数量
 * 
 * 批量处理策略：
 * - 收集一定数量的消息后批量处理（或定时批量处理）
 * - 按fileId分组，便于批量插入
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoDanmuConsumer {

    private final VideoDanmuMapper<VideoDanmu, ?> videoDanmuMapper;
    private final VideoInfoMapper<?, ?> videoInfoMapper;
    private final BloomFilterComponent bloomFilterComponent;
    private final EsSearchComponent esSearchComponent;
    private final RedisUtils<Object> redisUtils;

    // 批量处理缓冲区（按fileId分组）
    private final Map<String, List<VideoDanmuTaskDTO>> batchBuffer = new HashMap<>();
    
    // 批量处理阈值：每批处理50条弹幕
    private static final int BATCH_SIZE = 50;
    
    // 批量处理时间间隔：每5秒处理一次
    private static final long BATCH_INTERVAL = 5000L;
    private long lastProcessTime = System.currentTimeMillis();

    /**
     * 处理接收到的弹幕消息
     *
     * @param message JSON 格式的 VideoDanmuTaskDTO 字符串
     */
    @RabbitListener(queues = Constants.RABBITMQ_QUEUE_VIDEO_DANMU)
    public void onMessage(String message) {
        try {
            // 将 JSON 字符串转换为对象
            VideoDanmuTaskDTO danmuTaskDTO = JsonUtils.convertJson2Obj(message, VideoDanmuTaskDTO.class);
            
            if (danmuTaskDTO == null) {
                log.error("弹幕消息解析失败，message={}", message);
                return;
            }

            log.debug("收到弹幕任务消息, videoId={}, fileId={}, userId={}", 
                danmuTaskDTO.getVideoId(), danmuTaskDTO.getFileId(), danmuTaskDTO.getUserId());

            // 幂等性检查：防止MQ重复投递导致重复处理
            String businessKey = danmuTaskDTO.getFileId() + ":" + 
                               danmuTaskDTO.getUserId() + ":" + 
                               danmuTaskDTO.getPostTime().getTime();
            String key = "processed:danmu:" + businessKey;
            
            if (!redisUtils.setIfAbsent(key, "1", 7 * 24 * 3600 * 1000L)) {
                log.warn("弹幕已处理过，跳过, key={}", key);
                return;
            }

            // 添加到批量处理缓冲区
            synchronized (batchBuffer) {
                String fileId = danmuTaskDTO.getFileId();
                batchBuffer.computeIfAbsent(fileId, k -> new ArrayList<>()).add(danmuTaskDTO);

                // 检查是否需要批量处理
                int totalSize = batchBuffer.values().stream()
                    .mapToInt(List::size)
                    .sum();
                
                long currentTime = System.currentTimeMillis();
                boolean shouldProcess = totalSize >= BATCH_SIZE || 
                    (currentTime - lastProcessTime) >= BATCH_INTERVAL;
                
                if (shouldProcess) {
                    processBatch();
                }
            }

        } catch (Exception e) {
            log.error("消费弹幕任务消息失败, message={}", message, e);
        }
    }

    /**
     * 批量处理弹幕
     */
    @Transactional(rollbackFor = Exception.class)
    public void processBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }

        try {
            // 复制当前缓冲区并清空
            Map<String, List<VideoDanmuTaskDTO>> currentBatch;
            synchronized (batchBuffer) {
                currentBatch = new HashMap<>(batchBuffer);
                batchBuffer.clear();
                lastProcessTime = System.currentTimeMillis();
            }

            // 按fileId分组处理
            for (Map.Entry<String, List<VideoDanmuTaskDTO>> entry : currentBatch.entrySet()) {
                String fileId = entry.getKey();
                List<VideoDanmuTaskDTO> danmuList = entry.getValue();

                if (danmuList.isEmpty()) {
                    continue;
                }

                // 转换为VideoDanmu实体列表
                List<VideoDanmu> videoDanmuList = danmuList.stream()
                    .map(this::convertToVideoDanmu)
                    .collect(Collectors.toList());

                // 批量插入MySQL
                int insertCount = videoDanmuMapper.insertBatch(videoDanmuList);
                log.debug("批量插入弹幕成功, fileId={}, count={}", fileId, insertCount);

                // 加入布隆过滤器（批量插入后，danmuId已由数据库自动生成）
                for (VideoDanmu danmu : videoDanmuList) {
                    if (danmu.getDanmuId() != null) {
                        bloomFilterComponent.addDanmu(danmu.getDanmuId());
                    } else {
                        log.warn("弹幕插入后danmuId为空, fileId={}, videoId={}", 
                            danmu.getFileId(), danmu.getVideoId());
                    }
                }

                // 更新视频弹幕数量（按videoId分组统计）
                Map<String, Long> videoIdCountMap = danmuList.stream()
                    .collect(Collectors.groupingBy(
                        VideoDanmuTaskDTO::getVideoId,
                        Collectors.counting()
                    ));

                for (Map.Entry<String, Long> countEntry : videoIdCountMap.entrySet()) {
                    String videoId = countEntry.getKey();
                    Long count = countEntry.getValue();
                    
                    // 更新视频弹幕数量
                    videoInfoMapper.updateCountInfo(videoId, UserActionTypeEnum.VIDEO_DANMU.getField(), count.intValue());
                    
                    // 更新ES弹幕数量
                    esSearchComponent.updateDocCount(videoId, SearchOrderTypeEnum.VIDEO_DANMU.getField(), count.intValue());
                }

                log.debug("批量处理弹幕完成, fileId={}, count={}", fileId, danmuList.size());
            }

        } catch (Exception e) {
            log.error("批量处理弹幕失败", e);
            throw e;
        }
    }

    /**
     * 将DTO转换为实体
     */
    private VideoDanmu convertToVideoDanmu(VideoDanmuTaskDTO dto) {
        VideoDanmu danmu = new VideoDanmu();
        danmu.setVideoId(dto.getVideoId());
        danmu.setFileId(dto.getFileId());
        danmu.setUserId(dto.getUserId());
        danmu.setPostTime(dto.getPostTime());
        danmu.setText(dto.getText());
        danmu.setMode(dto.getMode());
        danmu.setColor(dto.getColor());
        danmu.setTime(dto.getTime());
        return danmu;
    }
}

