package com.easylive.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;
import com.easylive.cache.ThreeLevelCacheManager;
import com.easylive.component.EsSearchComponent;
import com.easylive.component.RedisComponent;
import com.easylive.entity.config.AppConfig;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.enums.SearchOrderTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.enums.VideoRecommendTypeEnum;
import com.easylive.entity.po.*;
import com.easylive.entity.query.*;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.*;
import com.easylive.service.UserInfoService;
import com.easylive.redis.RedisUtils;
import com.easylive.redis.BloomFilterComponent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.service.VideoInfoService;
import com.easylive.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;


/**
 * 视频信息 业务接口实现
 */
@Service("videoInfoService")
@Slf4j
@RequiredArgsConstructor
public class VideoInfoServiceImpl implements VideoInfoService {

	private static ExecutorService executorService = Executors.newFixedThreadPool(10);

	private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

	private final VideoInfoPostMapper<VideoInfoPost, VideoInfoPostQuery> videoInfoPostMapper;

	private final EsSearchComponent esSearchComponent;

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<VideoInfo> findListByParam(VideoInfoQuery param) {
		return this.videoInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(VideoInfoQuery param) {
		return this.videoInfoMapper.selectCount(param);
	}

	private final RedisComponent redisComponent;

	private final UserInfoService userInfoService;

	private final VideoCommentMapper<VideoComment, VideoCommentQuery> videoCommentMapper;

	private final VideoInfoFileMapper<VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

	private final VideoDanmuMapper<VideoDanmu, VideoDanmuQuery> videoDanmuMapper;

	private final AppConfig appConfig;

	private final VideoInfoFilePostMapper<VideoInfoFilePost, VideoInfoFilePostQuery> videoInfoFilePostMapper;

	private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	private final BloomFilterComponent bloomFilterComponent;

	private final ThreeLevelCacheManager threeLevelCacheManager;

	/**
	 * 缓存键前缀
	 */
	private static final String CACHE_KEY_PREFIX = "video_info:";

	private final RedisUtils<Object> redisUtils;

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<VideoInfo> findListByPage(VideoInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<VideoInfo> list = this.findListByParam(param);
		PaginationResultVO<VideoInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(VideoInfo bean) {
		return this.videoInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<VideoInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<VideoInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(VideoInfo bean, VideoInfoQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(VideoInfoQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据VideoId获取对象（使用三级缓存）
	 * 缓存策略：L1(Caffeine) -> L2(Redis) -> L3(MySQL)
	 * 结合布隆过滤器防缓存穿透（参考 music 项目风格）
	 */
	@Override
	public VideoInfo getVideoInfoByVideoId(String videoId) {
		String cacheKey = CACHE_KEY_PREFIX + videoId;

		VideoInfo videoInfo = null;
		
		// 1) 布隆过滤器未命中时，容错一次数据库查询并回填布隆与缓存（防止过滤器未预热）
		if (!bloomFilterComponent.mightContainVideo(videoId)) {
			log.debug("布隆过滤器判定视频不存在，尝试回源校验，videoId: {}", videoId);
			videoInfo = this.videoInfoMapper.selectByVideoId(videoId);
			bloomFilterComponent.addVideo(videoId);
			if (videoInfo != null) {
				threeLevelCacheManager.put(cacheKey, videoInfo);
			}
		} else {
			videoInfo = threeLevelCacheManager.get(cacheKey, key -> {
				// 从MySQL加载数据
				log.info("从MySQL加载视频信息，videoId: {}", videoId);
				VideoInfo info = this.videoInfoMapper.selectByVideoId(videoId);
				// 2) 查询完毕无论是否存在，都写入布隆过滤器，避免重复穿透
				bloomFilterComponent.addVideo(videoId);
				return info;
			});
		}
		
		// 3) 如果查询到数据，加上Redis中的增量值（实时显示最新计数）
		if (videoInfo != null) {
			addRedisIncrement(videoInfo, videoId);
		}
		
		return videoInfo;
	}
	
	/**
	 * 加上Redis中的增量值，实现实时显示最新计数
	 */
	private void addRedisIncrement(VideoInfo videoInfo, String videoId) {
		try {
			// 获取Redis中的点赞增量值
			Long likeIncrement = redisUtils.hGet(Constants.REDIS_KEY_VIDEO_LIKE_HASH, videoId);
			if (likeIncrement != null && likeIncrement != 0) {
				Integer currentLikeCount = videoInfo.getLikeCount() != null ? videoInfo.getLikeCount() : 0;
				videoInfo.setLikeCount(currentLikeCount + likeIncrement.intValue());
			}
			
			// 获取Redis中的收藏增量值
			Long collectIncrement = redisUtils.hGet(Constants.REDIS_KEY_VIDEO_COLLECT_HASH, videoId);
			if (collectIncrement != null && collectIncrement != 0) {
				Integer currentCollectCount = videoInfo.getCollectCount() != null ? videoInfo.getCollectCount() : 0;
				videoInfo.setCollectCount(currentCollectCount + collectIncrement.intValue());
			}
		} catch (Exception e) {
			log.error("获取Redis增量值失败, videoId={}", videoId, e);
			// 获取失败不影响主流程，只记录日志
		}
	}

	/**
	 * 根据VideoId修改（更新时删除缓存）
	 */
	@Override
	public Integer updateVideoInfoByVideoId(VideoInfo bean, String videoId) {
		Integer result = this.videoInfoMapper.updateByVideoId(bean, videoId);
		// 更新成功后，删除三级缓存
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX + videoId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("更新视频信息，删除缓存，videoId: {}", videoId);
		}
		return result;
	}

	/**
	 * 根据VideoId删除（删除时删除缓存）
	 */
	@Override
	public Integer deleteVideoInfoByVideoId(String videoId) {
		Integer result = this.videoInfoMapper.deleteByVideoId(videoId);
		// 删除成功后，删除三级缓存
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX + videoId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("删除视频信息，删除缓存，videoId: {}", videoId);
		}
		return result;
	}

	@Override
	public void changeInteraction(String videoId, String userId, String interaction) {
		VideoInfo videoInfo = new VideoInfo();
		videoInfo.setInteraction(interaction);
		VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
		videoInfoQuery.setVideoId(videoId);
		videoInfoQuery.setUserId(userId);
		//因为前端修改了信息，，videoinfo数据库也要做相应的修改
		videoInfoMapper.updateByParam(videoInfo, videoInfoQuery);

		VideoInfoPost videoInfoPost = new VideoInfoPost();
		videoInfoPost.setInteraction(interaction);
		VideoInfoPostQuery videoInfoPostQuery = new VideoInfoPostQuery();
		videoInfoPostQuery.setVideoId(videoId);
		videoInfoPostQuery.setUserId(userId);
		//因为前端修改了信息，，videoinfopost数据库也要做相应的修改
		videoInfoPostMapper.updateByParam(videoInfoPost, videoInfoPostQuery);
		
		// 更新成功后，删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX + videoId;
		threeLevelCacheManager.evict(cacheKey);
		log.info("修改视频互动设置，删除缓存，videoId: {}", videoId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteVideo(String videoId, String userId) {
		VideoInfoPost videoInfoPost = videoInfoPostMapper.selectByVideoId(videoId);
		//因为删除视频的时候，用户可能不是视频的作者.必须加上userId!=null,因为userId=null的时候表示是管理员

		if(videoInfoPost==null){
			throw new BusinessException(ResponseCodeEnum.CODE_404);
		}

		if (userId!=null&&videoInfoPost.getUserId()!=null&&!videoInfoPost.getUserId().equals(userId)) {
			throw new BusinessException(ResponseCodeEnum.CODE_404);
		}

		this.videoInfoMapper.deleteByVideoId(videoId);

		this.videoInfoPostMapper.deleteByVideoId(videoId);

		//删除videoinfo表
		videoInfoMapper.deleteByVideoId(videoId);

		//删除videoinfopost表
		videoInfoPostMapper.deleteByVideoId(videoId);

		//删除硬币
		SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
		userInfoMapper.updateCoinCountInfo(videoInfoPost.getUserId(), -sysSettingDto.getPostVideoCoinCount());


		//删除es信息
		esSearchComponent.deleteDoc(videoId);

		// 删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX + videoId;
		threeLevelCacheManager.evict(cacheKey);
		log.info("删除视频，删除缓存，videoId: {}", videoId);

		executorService.execute(()->{
			VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
			videoInfoFileQuery.setVideoId(videoId);
			//删除分片文件
			videoInfoFileMapper.deleteByParam(videoInfoFileQuery);

			//删除分片提交文件
			VideoInfoFilePostQuery videoInfoFilePostQuery = new VideoInfoFilePostQuery();
			videoInfoFilePostQuery.setVideoId(videoId);
			videoInfoFilePostMapper.deleteByParam(videoInfoFilePostQuery);

			//删除弹幕
			VideoDanmuQuery videoDanmuQuery = new VideoDanmuQuery();
			videoDanmuQuery.setVideoId(videoId);
			videoDanmuMapper.deleteByParam(videoDanmuQuery);

			//删除评论
			VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
			videoCommentQuery.setVideoId(videoId);
			videoCommentMapper.deleteByParam(videoCommentQuery);

			//删除本地视频文件
			List<VideoInfoFile> videoInfoFileList = this.videoInfoFileMapper.selectList(videoInfoFileQuery);

			for(VideoInfoFile item:videoInfoFileList){
				try {
					FileUtils.deleteDirectory(new File(appConfig.getProjectFolder()+item.getFilePath()));
				} catch (IOException e) {
					log.error("删除文件失败", e);
				}
			}

		});

	}

	//增加播放数量
	@Override
	public void addReadCount(String videoId) {
		videoInfoMapper.updateCountInfo(videoId, UserActionTypeEnum.VIDEO_PLAY.getField(), 1);
	}


	public void recommendVideo(String videoId) {
		VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
		if(videoInfo== null){
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		Integer recommendType = null;
		if(videoInfo.getRecommendType().equals(VideoRecommendTypeEnum.RECOMMEND.getType())){
			recommendType = VideoRecommendTypeEnum.NO_RECOMMEND.getType();
		}
		else{
			recommendType = VideoRecommendTypeEnum.RECOMMEND.getType();
		}

		VideoInfo updateInfo = new VideoInfo();
		updateInfo.setRecommendType(recommendType);
		videoInfoMapper.updateByVideoId(updateInfo, videoId);
		
		// 更新推荐状态后，删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX + videoId;
		threeLevelCacheManager.evict(cacheKey);
		log.info("更新视频推荐状态，删除缓存，videoId: {}", videoId);

	}

	/**
	 * 点赞 / 收藏 异步写 + 写聚合落库
	 * 从 Redis Hash 中拉取增量，批量刷新 MySQL 与 ES。
	 */
	@Override
	public void flushLikeCollectAgg() {
		// 1. 拉取点赞增量
		Map<String, Long> likeDeltaMap = redisUtils.hGetAllAndDelete(Constants.REDIS_KEY_VIDEO_LIKE_HASH);
		// 2. 拉取收藏增量
		Map<String, Long> collectDeltaMap = redisUtils.hGetAllAndDelete(Constants.REDIS_KEY_VIDEO_COLLECT_HASH);

		// 3. 批量刷新点赞数
		if (likeDeltaMap != null && !likeDeltaMap.isEmpty()) {
			likeDeltaMap.forEach((videoId, delta) -> {
				if (delta != null && delta != 0L) {
					videoInfoMapper.updateCountInfo(videoId, UserActionTypeEnum.VIDEO_LIKE.getField(), delta.intValue());
				}
			});
		}

		// 4. 批量刷新收藏数（同时更新 ES 排序字段）
		if (collectDeltaMap != null && !collectDeltaMap.isEmpty()) {
			collectDeltaMap.forEach((videoId, delta) -> {
				if (delta != null && delta != 0L) {
					int change = delta.intValue();
					videoInfoMapper.updateCountInfo(videoId, UserActionTypeEnum.VIDEO_COLLECT.getField(), change);
					// 更新 ES 中的收藏数，用于排序
					esSearchComponent.updateDocCount(videoId, SearchOrderTypeEnum.VIDEO_COLLECT.getField(), change);
				}
			});
		}
	}
} 