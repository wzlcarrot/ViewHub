package com.easylive.component;

import com.easylive.entity.config.AppConfig;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UploadingFileDto;
import com.easylive.entity.dto.VideoPlayInfoDto;
import com.easylive.entity.enums.DateTimePatternEnum;
import com.easylive.entity.po.CategoryInfo;
import com.easylive.entity.po.VideoDanmu;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.entity.po.VideoInfoPost;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.DateUtil;
import com.easylive.utils.StringTools;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

import com.easylive.entity.constant.Constants;
/*
* 这段代码定义了一个名为 RedisComponent 的类，位于包 com.easylive.component 中。
* 该类是一个 Spring 组件（@Component），用于封装与 Redis 数据库交互的业务逻辑，
* 这里主要是一些具体的逻辑，如保存验证码、获取验证码、清除验证码等。
* */
@Component
@RequiredArgsConstructor
public class RedisComponent {

    private final RedisUtils redisUtils;

    private final AppConfig appConfig;

    //保存验证码
    public String saveCheckCode(String code) {
        //使用随机的字符串创建一个唯一的 key
        String checkCodeKey = UUID.randomUUID().toString();
        //保存验证码
        redisUtils.setex(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey, code, Constants.REDIS_KEY_EXPIRE_ONE_MIN * 10);

        return checkCodeKey;
    }

    //获取验证码
    public String getCheckCode(String checkCodeKey) {
        return (String) redisUtils.get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
    }

    //清除验证码
    public void cleanCheckCode(String checkCodeKey) {
        redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
    }

    public void saveTokenInfo(TokenUserInfoDto tokenUserInfoDto) {
        String token = UUID.randomUUID().toString(); //创建一个唯一的 token
        tokenUserInfoDto.setExpireAt(System.currentTimeMillis() + Constants.REDIS_KEY_EXPIRE_ONE_DAY * 7);
        tokenUserInfoDto.setToken(token);

        //存放一个token 对应的 tokenUserInfoDto并且设置过期时间
        redisUtils.setex(Constants.REDIS_KEY_TOKEN_WEB + token, tokenUserInfoDto, Constants.REDIS_KEY_EXPIRE_ONE_DAY * 7);


    }

    public void cleanToken(String token) {
        //存放一个token 对应的 tokenUserInfoDto并且设置过期时间
        redisUtils.delete(Constants.REDIS_KEY_TOKEN_WEB + token);

    }

    public void cleanToken4Admin(String token) {
        //存放一个token 对应的 tokenUserInfoDto并且设置过期时间
        redisUtils.delete(Constants.REDIS_KEY_TOKEN_ADMIN + token);
    }

    //获取token 对应的 tokenUserInfoDto
    public TokenUserInfoDto getTokenInfo(String token) {
        return (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_TOKEN_WEB + token);
    }

    //也不是叫保存token。。其实token在键中..这段代码的功能是为管理员账户生成并保存一个唯一的访问令牌(token)。
    public String saveTokenInfo4Admin(String account) {
        String token = UUID.randomUUID().toString(); //创建一个唯一的 token

        redisUtils.setex(Constants.REDIS_KEY_TOKEN_ADMIN + token, account, Constants.REDIS_KEY_EXPIRE_ONE_DAY);

        return token;
    }


    public String getTokenInfo4Admin(String token) {
        return (String) redisUtils.get(Constants.REDIS_KEY_TOKEN_WEB + token);
    }

    public void saveCategoryList(List<CategoryInfo> categoryInfoList) {
        redisUtils.set(Constants.REDIS_KEY_CATEGORY_LIST, categoryInfoList);
    }

    public List<CategoryInfo> getCategoryList() {
        return (List<CategoryInfo>) redisUtils.get(Constants.REDIS_KEY_CATEGORY_LIST);
    }

    public String savePreVideoFileInfo(String userId, String fileName, Integer chunks) {
        String uploadId = StringTools.getRandomString(Constants.length_15);
        UploadingFileDto fileDto = new UploadingFileDto();
        fileDto.setChunks(chunks);
        fileDto.setUploadId(uploadId);
        fileDto.setFileName(fileName);
        fileDto.setChunkIndex(0);
        String day = DateUtil.format(new Date(), DateTimePatternEnum.YYYY_MM_DD.getPattern());

        String filePath = day + "/" + userId + uploadId;
        //先上传到临时目录中，然后提交到正式目录中
        String folder = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_TEMP + filePath;

        File foldFile = new File(folder);

        if (foldFile.exists() == false) {
            foldFile.mkdirs();
        }

        fileDto.setFilePath(filePath);
        redisUtils.setex(Constants.REDIS_KEY_UP_LOADING_FILE + userId + uploadId, fileDto, Constants.REDIS_KEY_EXPIRE_ONE_DAY);

        return uploadId;

    }

    public UploadingFileDto getUploadingVideoFile(String userId, String uploadId) {
        return (UploadingFileDto) redisUtils.get(Constants.REDIS_KEY_UP_LOADING_FILE + userId + uploadId);
    }

    public SysSettingDto getSysSettingDto() {
        SysSettingDto sysSettingDto = (SysSettingDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        if (sysSettingDto == null) {
            sysSettingDto = new SysSettingDto();
        }
        return sysSettingDto;
    }

    //更新redis 中的上传文件信息的数据
    public void updateVideoFileInfo(String userId, UploadingFileDto fileDto) {
        redisUtils.setex(Constants.REDIS_KEY_UP_LOADING_FILE + userId + fileDto.getUploadId(), fileDto, Constants.REDIS_KEY_EXPIRE_ONE_DAY);
    }

    public void delVideoFileInfo(String userId, String uploadId) {
        redisUtils.delete(Constants.REDIS_KEY_UP_LOADING_FILE + userId + uploadId);
    }

    //通过过期时间来删除消息队列中对应的文件
    public void addFile2DelList(String videoId, List<String> filePathList) {
        redisUtils.lpushAll(Constants.REDIS_KEY_FILE_DEL + videoId, filePathList, Constants.REDIS_KEY_EXPIRE_ONE_DAY);
    }

    //通过存放最新视频文件到redis消息队列中。
    public void addFile2TransferQueue(List<VideoInfoFilePost> fileList) {
        redisUtils.lpushAll(Constants.REDIS_KEY_QUEUE_TRANSFER, fileList, 0);
    }

    public VideoInfoFilePost getFileFromTransferQueue() {
        return (VideoInfoFilePost) redisUtils.rpop(Constants.REDIS_KEY_QUEUE_TRANSFER);
    }

    public VideoPlayInfoDto getVideoPlayFromQueue() {
        return (VideoPlayInfoDto) redisUtils.rpop(Constants.REDIS_KEY_QUEUE_VIDEO_PLAY);
    }

    public List<String> getDelFileList(String videoId) {
        return redisUtils.getQueueList(Constants.REDIS_KEY_FILE_DEL + videoId);
    }

    public void cleanDelFileList(String videoId) {
        redisUtils.delete(Constants.REDIS_KEY_FILE_DEL + videoId);
    }


    /**
     * 视频播放在线人数统计（优化版：高并发场景）
     * 
     * 优化点：
     * 1. 使用SETNX + EXPIRE原子操作（避免竞态条件）
     * 2. 减少Redis操作次数
     * 3. 支持高并发场景
     * 
     * @param fileId 文件ID
     * @param deviceId 设备ID
     * @return 当前在线人数
     */
    public Integer reportVideoPlayOnline(String fileId, String deviceId) {
        String userPlayOnlineKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER, fileId, deviceId);
        String playOnlineCountKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE, fileId);

        // 使用SETNX + EXPIRE原子操作（高并发优化）
        // 如果key不存在，设置key并返回true；如果key已存在，返回false
        Boolean isNewUser = redisUtils.setIfAbsent(userPlayOnlineKey, fileId, Constants.REDIS_KEY_EXPIRE_ONE_SECONDS * 8);
        
        if (Boolean.TRUE.equals(isNewUser)) {
            // 新用户上线：计数器+1
            Long count = redisUtils.incrementex(playOnlineCountKey, Constants.REDIS_KEY_EXPIRE_ONE_SECONDS * 10);
            return count.intValue();
        } else {
            // 老用户：续期（延长过期时间）
            redisUtils.expire(userPlayOnlineKey, Constants.REDIS_KEY_EXPIRE_ONE_SECONDS * 8);
            redisUtils.expire(playOnlineCountKey, Constants.REDIS_KEY_EXPIRE_ONE_SECONDS * 10);
            
            // 获取当前在线人数
            Integer count = (Integer) redisUtils.get(playOnlineCountKey);
            return count == null ? 1 : count;
        }
    }

    //减少视频播放在线人数
    public void decrementPlayOnlineCount(String key) {
        redisUtils.decrement(key);
    }

    /**
     * 删除用户在线Key（用于WebSocket断开连接时）
     * 
     * @param userKey 用户在线Key
     */
    public void deleteUserOnlineKey(String userKey) {
        redisUtils.delete(userKey);
    }

    /**
     * 获取在线人数（用于WebSocket实时推送）
     * 
     * @param countKey 在线人数Key
     * @return 在线人数，如果不存在返回0
     */
    public Integer getOnlineCount(String countKey) {
        Integer count = (Integer) redisUtils.get(countKey);
        return count == null ? 0 : count;
    }

    /**
     * 减少在线人数（用于WebSocket断开连接时）
     * 
     * @param countKey 在线人数Key
     */
    public void decrementOnlineCount(String countKey) {
        redisUtils.decrement(countKey);
    }


    public void updateTokenInfo(TokenUserInfoDto tokenUserInfoDto) {
        redisUtils.setex(Constants.REDIS_KEY_TOKEN_WEB + tokenUserInfoDto.getToken(), tokenUserInfoDto, Constants.REDIS_KEY_EXPIRE_ONE_DAY);
    }


    //每次调用一次就会次数+1
    public void addKeyWordCount(String keyword) {
        redisUtils.zaddCount(Constants.REDIS_KEY_VIDEO_SEARCH_COUNT, keyword);

    }

    public List<String> getKeyWordList() {
        return redisUtils.getZSetList(Constants.REDIS_KEY_VIDEO_SEARCH_COUNT, 10);
    }

    /**
     * 添加视频播放统计任务（已改为RabbitMQ实现）
     *
     * @deprecated 此方法已废弃，请使用 VideoPlayProducer.sendVideoPlayTask()
     * 保留此方法是为了兼容性，实际已改为发送到RabbitMQ
     */
    @Deprecated
    public void addVideoPlay(VideoPlayInfoDto videoPlayInfoDto) {
        // 已改为RabbitMQ实现，此方法保留用于兼容
        // 实际应该使用 VideoPlayProducer.sendVideoPlayTask()
        redisUtils.lpush(Constants.REDIS_KEY_QUEUE_VIDEO_PLAY, videoPlayInfoDto, null);
    }

    //记录视频播放次数，每天，同时设置过期时间
    public void recordVideoPlayCount(String videoId) {
        String date = DateUtil.format(new Date(), DateTimePatternEnum.YYYY_MM_DD.getPattern());
        redisUtils.incrementex(Constants.REDIS_KEY_VIDEO_PLAY_COUNT + date + videoId, Constants.REDIS_KEY_EXPIRE_ONE_DAY);

    }

    //获取每天的视频播放总次数
    public Map<String, Integer> getVideoPlayCount(String date) {
        Map<String, Integer> videoPlayMap = redisUtils.getBatch(Constants.REDIS_KEY_VIDEO_PLAY_COUNT + date);

        return videoPlayMap;
    }

    //保存系统设置到redis中
    public void saveSetting(SysSettingDto sysSettingDto) {
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
    }

    /**
     * 添加弹幕到Redis List（按fileId分组）
     * 用于实时弹幕展示，立即返回给用户
     *
     * @param fileId 文件ID
     * @param danmu  弹幕对象
     */
    public void addDanmuToRedis(String fileId, VideoDanmu danmu) {
        String key = Constants.REDIS_KEY_DANMU_LIST + fileId;
        // 使用lpush添加到列表头部，设置过期时间为7天
        redisUtils.lpush(key, danmu, Constants.REDIS_KEY_EXPIRE_ONE_DAY * 7L);
    }

    /**
     * 从Redis获取弹幕列表（按fileId）
     *
     * @param fileId 文件ID
     * @return 弹幕列表
     */
    public List<VideoDanmu> getDanmuListFromRedis(String fileId) {
        String key = Constants.REDIS_KEY_DANMU_LIST + fileId;
        List<VideoDanmu> list = redisUtils.getQueueList(key);
        return list == null ? new ArrayList<>() : list;
    }
}
