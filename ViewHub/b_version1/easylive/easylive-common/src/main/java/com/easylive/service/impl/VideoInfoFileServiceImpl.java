package com.easylive.service.impl;

import lombok.RequiredArgsConstructor;
import java.util.List;

import com.easylive.cache.ThreeLevelCacheManager;
import com.easylive.redis.BloomFilterComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.query.VideoInfoFileQuery;
import com.easylive.entity.po.VideoInfoFile;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.query.SimplePage;
import com.easylive.mappers.VideoInfoFileMapper;
import com.easylive.service.VideoInfoFileService;
import com.easylive.utils.StringTools;


/**
 * 视频文件信息 业务接口实现
 */
@Service("videoInfoFileService")
@Slf4j
@RequiredArgsConstructor
public class VideoInfoFileServiceImpl implements VideoInfoFileService {

	private final VideoInfoFileMapper<VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

	private final ThreeLevelCacheManager threeLevelCacheManager;

	private final BloomFilterComponent bloomFilterComponent;

	/**
	 * 视频文件信息缓存键前缀
	 */
	private static final String CACHE_KEY_PREFIX_FILE = "video_file:";

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<VideoInfoFile> findListByParam(VideoInfoFileQuery param) {
		return this.videoInfoFileMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(VideoInfoFileQuery param) {
		return this.videoInfoFileMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<VideoInfoFile> findListByPage(VideoInfoFileQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<VideoInfoFile> list = this.findListByParam(param);
		PaginationResultVO<VideoInfoFile> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(VideoInfoFile bean) {
		return this.videoInfoFileMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<VideoInfoFile> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoFileMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<VideoInfoFile> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoFileMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(VideoInfoFile bean, VideoInfoFileQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoFileMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(VideoInfoFileQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoFileMapper.deleteByParam(param);
	}

	/**
	 * 根据FileId获取对象（使用三级缓存）
	 * 场景：视频文件信息查询频繁，适合缓存
	 */
	@Override
	public VideoInfoFile getVideoInfoFileByFileId(String fileId) {
		String cacheKey = CACHE_KEY_PREFIX_FILE + fileId;

		// 布隆过滤器未命中时，容错一次数据库查询并回填布隆与缓存
		if (!bloomFilterComponent.mightContainFile(fileId)) {
			log.debug("布隆过滤器判定视频文件不存在，尝试回源校验，fileId: {}", fileId);
			VideoInfoFile dbInfo = this.videoInfoFileMapper.selectByFileId(fileId);
			bloomFilterComponent.addFile(fileId);
			if (dbInfo != null) {
				threeLevelCacheManager.put(cacheKey, dbInfo);
			}
			return dbInfo;
		}

		return threeLevelCacheManager.get(cacheKey, key -> {
			log.info("从MySQL加载视频文件信息，fileId: {}", fileId);
			VideoInfoFile infoFile = this.videoInfoFileMapper.selectByFileId(fileId);
			bloomFilterComponent.addFile(fileId);
			return infoFile;
		});
	}

	/**
	 * 根据FileId修改（更新时删除缓存）
	 */
	@Override
	public Integer updateVideoInfoFileByFileId(VideoInfoFile bean, String fileId) {
		Integer result = this.videoInfoFileMapper.updateByFileId(bean, fileId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_FILE + fileId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("更新视频文件信息，删除缓存，fileId: {}", fileId);
		}
		return result;
	}

	/**
	 * 根据FileId删除（删除时删除缓存）
	 */
	@Override
	public Integer deleteVideoInfoFileByFileId(String fileId) {
		Integer result = this.videoInfoFileMapper.deleteByFileId(fileId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_FILE + fileId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("删除视频文件信息，删除缓存，fileId: {}", fileId);
		}
		return result;
	}
}