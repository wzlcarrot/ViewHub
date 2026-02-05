package com.easylive.service.impl;

import com.easylive.component.EsSearchComponent;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.VideoDanmuTaskDTO;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.VideoDanmu;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.VideoDanmuQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.VideoDanmuMapper;
import com.easylive.mappers.VideoInfoMapper;
import com.easylive.mq.VideoDanmuProducer;
import com.easylive.redis.BloomFilterComponent;
import lombok.RequiredArgsConstructor;
import com.easylive.service.VideoDanmuService;
import com.easylive.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 视频弹幕 业务接口实现
 */
@Service("videoDanmuService")
@Slf4j
@RequiredArgsConstructor
public class VideoDanmuServiceImpl implements VideoDanmuService {

    private final VideoDanmuMapper<VideoDanmu, VideoDanmuQuery> videoDanmuMapper;

    private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final EsSearchComponent esSearchComponent;

    private final BloomFilterComponent bloomFilterComponent;

    private final RedisComponent redisComponent;

    private final VideoDanmuProducer videoDanmuProducer;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<VideoDanmu> findListByParam(VideoDanmuQuery param) {
        return this.videoDanmuMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(VideoDanmuQuery param) {
        return this.videoDanmuMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<VideoDanmu> findListByPage(VideoDanmuQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<VideoDanmu> list = this.findListByParam(param);
        PaginationResultVO<VideoDanmu> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(VideoDanmu bean) {
        return this.videoDanmuMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<VideoDanmu> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.videoDanmuMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<VideoDanmu> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.videoDanmuMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(VideoDanmu bean, VideoDanmuQuery param) {
        StringTools.checkParam(param);
        return this.videoDanmuMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(VideoDanmuQuery param) {
        StringTools.checkParam(param);
        return this.videoDanmuMapper.deleteByParam(param);
    }

    /**
     * 根据DanmuId获取对象（使用布隆过滤器防穿透）
     * 场景：弹幕查询频繁，弹幕数量巨大，防止恶意查询不存在的弹幕ID
     */
    @Override
    public VideoDanmu getVideoDanmuByDanmuId(Integer danmuId) {
        // 1. 布隆过滤器判断
        if (!bloomFilterComponent.mightContainDanmu(danmuId)) {
            log.debug("布隆过滤器判断弹幕不存在, danmuId={}", danmuId);
            return null;
        }

        // 2. 查询数据库
        VideoDanmu danmu = this.videoDanmuMapper.selectByDanmuId(danmuId);
        
        // 3. 无论是否存在，都加入布隆过滤器（防止重复穿透）
        bloomFilterComponent.addDanmu(danmuId);
        
        return danmu;
    }

    /**
     * 根据DanmuId修改
     */
    @Override
    public Integer updateVideoDanmuByDanmuId(VideoDanmu bean, Integer danmuId) {
        return this.videoDanmuMapper.updateByDanmuId(bean, danmuId);
    }

    /**
     * 根据DanmuId删除
     */
    @Override
    public Integer deleteVideoDanmuByDanmuId(Integer danmuId) {
        return this.videoDanmuMapper.deleteByDanmuId(danmuId);
    }

    /**
     * 保存弹幕（优化后的流程）
     *
     * 写入流程（数据优先策略）：
     * 1. 先发送到 RabbitMQ（保证数据持久化）- 失败则整体失败
     * 2. 再写入 Redis（提供实时查询）- MQ成功后才写入
     * 3. 消费者批量写入 MySQL - 批量 INSERT，效率高
     * 4. 加入布隆过滤器
     *
     * 优点：优先保证数据不丢失，避免"MQ失败但Redis成功"的不一致场景
     */
    @Override
    public void saveVideoDanmu(VideoDanmu bean) {
        // 首先查找这个视频是否存在
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(bean.getVideoId());
        if (videoInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        // 是否关闭弹幕   0是关闭评论，1是关闭弹幕
        if (videoInfo.getInteraction() != null && videoInfo.getInteraction().contains(Constants.ONE.toString())) {
            throw new BusinessException("UP主已关闭弹幕");
        }

        // 1. 优先发送到 RabbitMQ（保证数据持久化）- 失败则整体失败
        VideoDanmuTaskDTO danmuTaskDTO = new VideoDanmuTaskDTO();
        danmuTaskDTO.setVideoId(bean.getVideoId());
        danmuTaskDTO.setFileId(bean.getFileId());
        danmuTaskDTO.setUserId(bean.getUserId());
        danmuTaskDTO.setPostTime(bean.getPostTime());
        danmuTaskDTO.setText(bean.getText());
        danmuTaskDTO.setMode(bean.getMode());
        danmuTaskDTO.setColor(bean.getColor());
        danmuTaskDTO.setTime(bean.getTime());

        try {
            videoDanmuProducer.sendDanmuTask(danmuTaskDTO);
            log.debug("弹幕任务已发送到RabbitMQ, fileId={}, videoId={}",
                bean.getFileId(), bean.getVideoId());
        } catch (Exception e) {
            log.error("发送弹幕任务到RabbitMQ失败, fileId={}, videoId={}",
                bean.getFileId(), bean.getVideoId(), e);
            // MQ发送失败，抛出异常，Redis不写入，保证数据一致性
            throw new BusinessException("发送弹幕失败，请重试");
        }

        // 2. MQ成功后，再写入 Redis（List，按 fileId 分组）- 提供实时查询
        redisComponent.addDanmuToRedis(bean.getFileId(), bean);
        log.debug("弹幕已写入Redis, fileId={}, videoId={}, userId={}",
            bean.getFileId(), bean.getVideoId(), bean.getUserId());

        // 注意：MySQL批量写入、布隆过滤器、更新视频弹幕数量、更新ES弹幕数量
        // 这些操作都在 VideoDanmuConsumer 中批量处理
    }

    @Override
    public void deleteDanmu(String userId, Integer danmuId) {
        VideoDanmu danmu = videoDanmuMapper.selectByDanmuId(danmuId);
        if (null == danmu) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(danmu.getVideoId());
        if (null == videoInfo) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (userId != null && !videoInfo.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        videoDanmuMapper.deleteByDanmuId(danmuId);
    }
}