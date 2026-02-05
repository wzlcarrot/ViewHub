package com.easylive.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.easylive.component.EsSearchComponent;
import com.easylive.component.RedisComponent;
import com.easylive.entity.config.AppConfig;
import com.easylive.mq.VideoTransferProducer;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.dto.UploadingFileDto;
import com.easylive.entity.enums.*;
import com.easylive.entity.po.*;
import com.easylive.entity.query.*;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.*;
import com.easylive.utils.CopyTools;
import com.easylive.utils.FFmpegUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.service.VideoInfoPostService;
import com.easylive.utils.StringTools;
import org.apache.commons.lang3.ArrayUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频信息 业务接口实现
 */
@Slf4j
@Service("videoInfoPostService")
@RequiredArgsConstructor
public class VideoInfoPostServiceImpl implements VideoInfoPostService {

	private final VideoInfoPostMapper<VideoInfoPost, VideoInfoPostQuery> videoInfoPostMapper;

	private final RedisComponent redisComponent;

	private final VideoInfoFilePostMapper<VideoInfoFilePost, VideoInfoFilePostQuery> videoInfoFilePostMapper;

	private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

	private final VideoInfoFileMapper<VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

	private final AppConfig  appConfig;

	private final FFmpegUtils ffmpegUtils;

	private final EsSearchComponent esSearchComponent;

	private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	private final VideoTransferProducer videoTransferProducer;

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<VideoInfoPost> findListByParam(VideoInfoPostQuery param) {
		return this.videoInfoPostMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(VideoInfoPostQuery param) {
		return this.videoInfoPostMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<VideoInfoPost> findListByPage(VideoInfoPostQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<VideoInfoPost> list = this.findListByParam(param);
		PaginationResultVO<VideoInfoPost> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(VideoInfoPost bean) {
		return this.videoInfoPostMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<VideoInfoPost> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoPostMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<VideoInfoPost> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.videoInfoPostMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(VideoInfoPost bean, VideoInfoPostQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoPostMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(VideoInfoPostQuery param) {
		StringTools.checkParam(param);
		return this.videoInfoPostMapper.deleteByParam(param);
	}

	/**
	 * 根据VideoId获取对象
	 */
	@Override
	public VideoInfoPost getVideoInfoPostByVideoId(String videoId) {
		return this.videoInfoPostMapper.selectByVideoId(videoId);
	}

	/**
	 * 根据VideoId修改
	 */
	@Override
	public Integer updateVideoInfoPostByVideoId(VideoInfoPost bean, String videoId) {
		return this.videoInfoPostMapper.updateByVideoId(bean, videoId);
	}

	/**
	 * 根据VideoId删除
	 */
	@Override
	public Integer deleteVideoInfoPostByVideoId(String videoId) {
		return this.videoInfoPostMapper.deleteByVideoId(videoId);
	}

	/*
	* 		我感觉可以这样理解。。为什么输入参数是VideoInfoPost videoInfoPost, List<VideoInfoFilePost> uploadFileList。
	*       第一个参数VideoInfoPost的意思是大概介绍一下这个视频合集的信息，比如名称，简介，标签，作者，分类，权限等
	*       第二个参数List<VideoInfoFilePost> uploadFileList的意思是上传的这个视频合集中的所有的视频信息。
	* */

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveVideoInfo(VideoInfoPost videoInfoPost, List<VideoInfoFilePost> uploadFileList) {
		System.out.println("uploadList:"+uploadFileList);

		//上传的视频数量大于限制的视频数量
		if(uploadFileList.size()>redisComponent.getSysSettingDto().getVideoPCount()){
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		//当提交成功后或者转码失败后不允许修改视频内容
		if(ArrayUtils.contains(new Integer[]{VideoStatusEnum.STATUS0.getStatus(),VideoStatusEnum.STATUS2.getStatus()}, videoInfoPost.getStatus())){
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		Date curDate = new Date();
		String videoId = videoInfoPost.getVideoId();
		List<VideoInfoFilePost> deleteFileList = new ArrayList<>();
		List<VideoInfoFilePost> addFileList = uploadFileList;

		if(StringTools.isEmpty(videoId)==true){
			//如果没找到视频信息，则新增
			videoId = StringTools.getRandomString(Constants.length_10);
			videoInfoPost.setVideoId(videoId);
			videoInfoPost.setCreateTime(curDate);
			videoInfoPost.setLastUpdateTime(curDate);

			videoInfoPost.setStatus(VideoStatusEnum.STATUS0.getStatus());
			//如果没有找到视频合集的信息，则新增
			videoInfoPostMapper.insert(videoInfoPost);
		}
		else{

			VideoInfoFilePostQuery fileQuery = new VideoInfoFilePostQuery();
			fileQuery.setVideoId(videoId);
			fileQuery.setUserId(videoInfoPost.getUserId());

			//查询现有的视频文件信息列表,因为要上传视频文件，，所以VideoInfoFilePost主要是记载单个视频文件信息   通过视频id号来找到所有小视频的信息
			List<VideoInfoFilePost> dbInfoFileList = this.videoInfoFilePostMapper.selectList(fileQuery);

			//uploadId作为键，value为单个小视频文件信息。
			Map<String, VideoInfoFilePost> uploadFileMap = uploadFileList.stream().
					collect(Collectors.toMap(VideoInfoFilePost::getUploadId, v -> v));

			//是否更改了文件名
			Boolean updateFileName = false;

			//遍历视频合集的所有小视频
			for(VideoInfoFilePost fileInfo:dbInfoFileList){
				//根据视频文件上传ID来找到视频信息
				VideoInfoFilePost updateFile = uploadFileMap.get(fileInfo.getUploadId());
				//如果没有找到小视频文件信息，也就是找到之前没有用的小视频信息数据，则删除
				if(updateFile==null){

					deleteFileList.add(fileInfo);
				}
				//如果文件名有修改
				else if(updateFile.getFileName().equals(fileInfo.getFileName())==false){
					updateFileName = true;
				}
			}
			//找出找到新增视频文件，也就是新增的小视频
			addFileList = uploadFileList.stream().filter(v -> dbInfoFileList.stream().
					filter(db -> db.getUploadId().equals(v.getUploadId())).count()==0).collect(Collectors.toList());

			//更新视频最后更新时间
			videoInfoPost.setLastUpdateTime(curDate);
			//视频提交中标题 封面 标签 简介这些信息是否修改
			Boolean changeVideoInfo = changeVideoInfo(videoInfoPost);

			if(addFileList.isEmpty()==false){
				//如果addFileList不为空，则说明有新增文件，则视频状态改为转码中
				videoInfoPost.setStatus(VideoStatusEnum.STATUS0.getStatus());

			}
			else if(changeVideoInfo||updateFileName){   //标题 封面 标签 简介修改，则视频状态改为待审核
				videoInfoPost.setStatus(VideoStatusEnum.STATUS2.getStatus());
			}
			//在MYSQL中更新视频信息
			videoInfoPostMapper.updateByVideoId(videoInfoPost, videoId);
		}

		if(deleteFileList.isEmpty()==false){
			List<String> delFileIdList = deleteFileList.stream().map(VideoInfoFilePost::getFileId).collect(Collectors.toList());
			//MYSQL数据库删除文件信息
			videoInfoFilePostMapper.deleteBatchByFileId(delFileIdList, videoInfoPost.getUserId());
			//本地删除文件
			List<String> delFilePathList = deleteFileList.stream().map(VideoInfoFilePost::getFilePath).collect(Collectors.toList());
			//把要删的文件放入到消息队列中
			redisComponent.addFile2DelList(videoId,delFilePathList);
		}

		//增加和删除和修改的操作处理完了。

		//更新小视频信息
		Integer index = 1;
		for (VideoInfoFilePost videoInfoFile : uploadFileList) {
			videoInfoFile.setFileIndex(index++);
			videoInfoFile.setVideoId(videoId);
			videoInfoFile.setUserId(videoInfoPost.getUserId());
			if (videoInfoFile.getFileId() == null) {
				videoInfoFile.setFileId(StringTools.getRandomString(Constants.length_20));
				videoInfoFile.setUpdateType(VideoFileUpdateTypeEnum.UPDATE.getStatus());
				videoInfoFile.setTransferResult(VideoFileTransferResultEnum.TRANSFER.getStatus());
			}
		}
		//在MYSQL中更新文件信息
		this.videoInfoFilePostMapper.insertOrUpdateBatch(uploadFileList);

		if (!addFileList.isEmpty()) {
			for (VideoInfoFilePost file : addFileList) {
				file.setUserId(videoInfoPost.getUserId());
				file.setVideoId(videoId);
			}
			// 发送转码任务到 RabbitMQ 队列（替换原来的 Redis List）
			videoTransferProducer.sendTransferTasks(addFileList);
		}

	}


	private boolean changeVideoInfo(VideoInfoPost videoInfoPost) {
		VideoInfoPost dbInfo = this.videoInfoPostMapper.selectByVideoId(videoInfoPost.getVideoId());
		//标题，封面，标签，简介
		if (!videoInfoPost.getVideoCover().equals(dbInfo.getVideoCover()) || !videoInfoPost.getVideoName().equals(dbInfo.getVideoName()) || !videoInfoPost.getTags().equals(dbInfo.getTags()) || !videoInfoPost.getIntroduction().equals(
				dbInfo.getIntroduction())) {
			return true;
		}
		return false;
	}

	//转码
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void transferVideoFile(VideoInfoFilePost videoInfoFile) {
		VideoInfoFilePost updateFilePost = new VideoInfoFilePost();
		try {
			UploadingFileDto fileDto = redisComponent.getUploadingVideoFile(videoInfoFile.getUserId(), videoInfoFile.getUploadId());
			/**
			 * 拷贝文件到正式目录
			 */
			String tempFilePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_TEMP + fileDto.getFilePath();

			File tempFile = new File(tempFilePath);

			String targetFilePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_VIDEO + fileDto.getFilePath();
			File taregetFile = new File(targetFilePath);
			if (!taregetFile.exists()) {
				taregetFile.mkdirs();
			}
			FileUtils.copyDirectory(tempFile, taregetFile);

			/**
			 * 删除临时目录
			 */
			FileUtils.forceDelete(tempFile);
			redisComponent.delVideoFileInfo(videoInfoFile.getUserId(), videoInfoFile.getUploadId());

			/**
			 * 合并文件
			 */
			String completeVideo = targetFilePath + Constants.TEMP_VIDEO_NAME;
			this.union(targetFilePath, completeVideo, true);

			/**
			 * 获取播放时长
			 */
			Integer duration = ffmpegUtils.getVideoInfoDuration(completeVideo);
			updateFilePost.setDuration(duration);
			updateFilePost.setFileSize(new File(completeVideo).length());
			updateFilePost.setFilePath(Constants.FILE_VIDEO + fileDto.getFilePath());
			updateFilePost.setTransferResult(VideoFileTransferResultEnum.SUCCESS.getStatus());

			/**
			 * ffmpeg切割文件
			 */
			this.convertVideo2Ts(completeVideo);
		} catch (Exception e) {
			log.error("文件转码失败", e);
			updateFilePost.setTransferResult(VideoFileTransferResultEnum.FAIL.getStatus());
		} finally {
			//更新文件状态
			videoInfoFilePostMapper.updateByUploadIdAndUserId(updateFilePost, videoInfoFile.getUploadId(), videoInfoFile.getUserId());
			//更新视频信息
			VideoInfoFilePostQuery fileQuery = new VideoInfoFilePostQuery();
			fileQuery.setVideoId(videoInfoFile.getVideoId());
			fileQuery.setTransferResult(VideoFileTransferResultEnum.FAIL.getStatus());
			Integer failCount = videoInfoFilePostMapper.selectCount(fileQuery);
			if (failCount > 0) {
				VideoInfoPost videoUpdate = new VideoInfoPost();
				videoUpdate.setStatus(VideoStatusEnum.STATUS1.getStatus());
				videoInfoPostMapper.updateByVideoId(videoUpdate, videoInfoFile.getVideoId());
				return;
			}
			fileQuery.setTransferResult(VideoFileTransferResultEnum.TRANSFER.getStatus());
			Integer transferCount = videoInfoFilePostMapper.selectCount(fileQuery);
			if (transferCount == 0) {
				Integer duration = videoInfoFilePostMapper.sumDuration(videoInfoFile.getVideoId());
				VideoInfoPost videoUpdate = new VideoInfoPost();
				videoUpdate.setStatus(VideoStatusEnum.STATUS2.getStatus());
				videoUpdate.setDuration(duration);
				videoInfoPostMapper.updateByVideoId(videoUpdate, videoInfoFile.getVideoId());
			}
		}
	}

	//审核视频
	@Transactional(rollbackFor = Exception.class)
	public void auditVideo(String videoId, Integer status, String reason) {
		VideoStatusEnum videoStatusEnum = VideoStatusEnum.getByStatus(status);
		//现在这个状态应该是有值的，如果没有值，则抛出异常
		if (videoStatusEnum == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}
		VideoInfoPost videoInfoPost = new VideoInfoPost();

		videoInfoPost.setStatus(status);
		VideoInfoPostQuery videoInfoPostQuery = new VideoInfoPostQuery();
		videoInfoPostQuery.setStatus(VideoStatusEnum.STATUS2.getStatus());
		videoInfoPostQuery.setVideoId(videoId);

		//其实这里实现了一个乐观锁，通过一个标志位来解决并发问题。
		Integer audioCount = this.videoInfoPostMapper.updateByParam(videoInfoPost, videoInfoPostQuery);
		if (audioCount == 0) {
			throw new BusinessException("审核失败，请稍后重试");
		}

		VideoInfoFilePost videoInfoFilePost = new VideoInfoFilePost();
		videoInfoFilePost.setUpdateType(VideoFileUpdateTypeEnum.NO_UPDATE.getStatus());

		VideoInfoFilePostQuery filePostQuery = new VideoInfoFilePostQuery();
		filePostQuery.setVideoId(videoId);
		videoInfoFilePostMapper.updateByParam(videoInfoFilePost,filePostQuery);

		//审核失败
		if(videoStatusEnum==VideoStatusEnum.STATUS4){
			return;
		}

		VideoInfoPost infoPost = videoInfoPostMapper.selectByVideoId(videoId);

		VideoInfo dbVideoInfo = videoInfoMapper.selectByVideoId(videoId);
		//说明是第一次审核
		if(dbVideoInfo==null){
			SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
			//给用户加硬币
			userInfoMapper.updateCoinCountInfo(infoPost.getUserId(), sysSettingDto.getPostVideoCoinCount());
		}
		/**
		 * 将发布信息复制到正式表信息
		 */
		VideoInfo videoInfo = CopyTools.copy(infoPost, VideoInfo.class);
		this.videoInfoMapper.insertOrUpdate(videoInfo);

		/**
		 * 更新视频信息 先删除再添加
		 */
		VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
		videoInfoFileQuery.setVideoId(videoId);
		this.videoInfoFileMapper.deleteByParam(videoInfoFileQuery);

		VideoInfoFilePostQuery videoInfoFilePostQuery = new VideoInfoFilePostQuery();
		videoInfoFilePostQuery.setVideoId(videoId);
		List<VideoInfoFilePost> videoInfoFilePostList = this.videoInfoFilePostMapper.selectList(videoInfoFilePostQuery);

		List<VideoInfoFile> videoInfoFileList = CopyTools.copyList(videoInfoFilePostList, VideoInfoFile.class);
		this.videoInfoFileMapper.insertBatch(videoInfoFileList);

		List<String> filePathList = redisComponent.getDelFileList(videoId);
		if (filePathList != null) {
			for (String path : filePathList) {
				File file = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER + path);
				if (file.exists()) {
					try {
						FileUtils.deleteDirectory(file);
					} catch (IOException e) {
						log.error("删除文件失败", e);
					}
				}
			}
		}

		redisComponent.cleanDelFileList(videoId);

		//当审核成功后，将视频信息保存到es中
		esSearchComponent.saveDoc(videoInfo);

		// 预索引：异步生成向量并写入ES（用于AI问答系统的向量检索）
		// 注意：需要在web模块的AIChatService中实现preIndexVideo方法
		// 可以通过消息队列或事件机制触发预索引
		// 示例：aiChatService.preIndexVideo(videoInfo);
		log.info("视频发布完成，建议触发预索引: videoId={}", videoId);

	}

	private void convertVideo2Ts(String videoFilePath) {
		File videoFile = new File(videoFilePath);
		//创建同名切片目录
		File tsFolder = videoFile.getParentFile();
		String codec = ffmpegUtils.getVideoCodec(videoFilePath);
		//转码
		if (Constants.VIDEO_CODE_HEVC.equals(codec)) {
			String tempFileName = videoFilePath + Constants.VIDEO_CODE_TEMP_FILE_SUFFIX;
			new File(videoFilePath).renameTo(new File(tempFileName));
			ffmpegUtils.convertHevc2Mp4(tempFileName, videoFilePath);
			new File(tempFileName).delete();
		}

		//视频转为ts
		ffmpegUtils.convertVideo2Ts(tsFolder, videoFilePath);

		//删除视频文件
		videoFile.delete();
	}


	public static void union(String dirPath, String toFilePath, boolean delSource) throws BusinessException {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			throw new BusinessException("目录不存在");
		}
		File fileList[] = dir.listFiles();
		File targetFile = new File(toFilePath);
		try (RandomAccessFile writeFile = new RandomAccessFile(targetFile, "rw")) {
			byte[] b = new byte[1024 * 10];
			for (int i = 0; i < fileList.length; i++) {
				int len = -1;
				//创建读块文件的对象
				File chunkFile = new File(dirPath + File.separator + i);
				RandomAccessFile readFile = null;
				try {
					readFile = new RandomAccessFile(chunkFile, "r");
					while ((len = readFile.read(b)) != -1) {
						writeFile.write(b, 0, len);
					}
				} catch (Exception e) {
					log.error("合并分片失败", e);
					throw new BusinessException("合并文件失败");
				} finally {
					readFile.close();
				}
			}
		} catch (Exception e) {
			throw new BusinessException("合并文件" + dirPath + "出错了");
		} finally {
			if (delSource) {
				for (int i = 0; i < fileList.length; i++) {
					fileList[i].delete();
				}
			}
		}
	}


}