package com.easylive.service.impl;

import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.StatisticsTypeEnum;
import com.easylive.entity.enums.UserActionTypeEnum;
import com.easylive.entity.po.StatisticsInfo;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.StatisticsInfoQuery;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.mappers.StatisticsInfoMapper;
import com.easylive.mappers.UserFocusMapper;
import com.easylive.mappers.UserInfoMapper;
import com.easylive.mappers.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import com.easylive.service.StatisticsInfoService;
import com.easylive.utils.DateUtil;
import com.easylive.utils.StringTools;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * 数据统计 业务接口实现
 */
@Service("statisticsInfoService")
@RequiredArgsConstructor
public class StatisticsInfoServiceImpl implements StatisticsInfoService {

    private final StatisticsInfoMapper<StatisticsInfo, StatisticsInfoQuery> statisticsInfoMapper;

    private final RedisComponent redisComponent;

    private final VideoInfoMapper videoInfoMapper;

    private final UserFocusMapper userFocusMapper;

    private final UserInfoMapper userInfoMapper;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<StatisticsInfo> findListByParam(StatisticsInfoQuery param) {
        return this.statisticsInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(StatisticsInfoQuery param) {
        return this.statisticsInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<StatisticsInfo> findListByPage(StatisticsInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<StatisticsInfo> list = this.findListByParam(param);
        PaginationResultVO<StatisticsInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(StatisticsInfo bean) {
        return this.statisticsInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<StatisticsInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.statisticsInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<StatisticsInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.statisticsInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(StatisticsInfo bean, StatisticsInfoQuery param) {
        StringTools.checkParam(param);
        return this.statisticsInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(StatisticsInfoQuery param) {
        StringTools.checkParam(param);
        return this.statisticsInfoMapper.deleteByParam(param);
    }

    /**
     * 根据StatisticsDateAndUserIdAndDataType获取对象
     */
    @Override
    public StatisticsInfo getStatisticsInfoByStatisticsDateAndUserIdAndDataType(String statisticsDate, String userId, Integer dataType) {
        return this.statisticsInfoMapper.selectByStatisticsDateAndUserIdAndDataType(statisticsDate, userId, dataType);
    }

    /**
     * 根据StatisticsDateAndUserIdAndDataType修改
     */
    @Override
    public Integer updateStatisticsInfoByStatisticsDateAndUserIdAndDataType(StatisticsInfo bean, String statisticsDate, String userId, Integer dataType) {
        return this.statisticsInfoMapper.updateByStatisticsDateAndUserIdAndDataType(bean, statisticsDate, userId, dataType);
    }

    /**
     * 根据StatisticsDateAndUserIdAndDataType删除
     */
    @Override
    public Integer deleteStatisticsInfoByStatisticsDateAndUserIdAndDataType(String statisticsDate, String userId, Integer dataType) {
        return this.statisticsInfoMapper.deleteByStatisticsDateAndUserIdAndDataType(statisticsDate, userId, dataType);
    }




    public void statisticsData() {
        List<StatisticsInfo> statisticsInfoList = new ArrayList<>();
        final String statisticsDate = DateUtil.getYesterday();
        //统计播放量
        Map<String, Integer> videoPlayCountMap = redisComponent.getVideoPlayCount(statisticsDate);//获取每个视频每天的播放次数
        List<String> playVideoKeys = new ArrayList<>(videoPlayCountMap.keySet());

        // 从Redis键名中提取视频ID
        List<String> videoIdList = new ArrayList<>();
        for (String redisKey : videoPlayCountMap.keySet()) {

            // 我们需要提取视频最后部分（video001）
            int lastColonPosition = redisKey.lastIndexOf(":");
            String videoId = redisKey.substring(lastColonPosition + 1);
            videoIdList.add(videoId);
        }
        System.out.println(videoIdList);


        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setVideoIdArray(playVideoKeys.toArray(new String[playVideoKeys.size()]));
        List<VideoInfo> videoInfoList = videoInfoMapper.selectList(videoInfoQuery);  //通过视频ID查询视频信息

        // 按用户ID分组统计播放量
        Map<String, Integer> videoCountMap = new HashMap<>();

        // 遍历所有视频信息
        for (VideoInfo videoInfo : videoInfoList) {
            String userId = videoInfo.getUserId();
            String videoId = videoInfo.getVideoId();

            // 构造Redis键名获取播放次数
            String redisKey = Constants.REDIS_KEY_VIDEO_PLAY_COUNT + statisticsDate + ":" + videoId;
            Integer playCount = videoPlayCountMap.get(redisKey);   //获取该视频的播放次数
            if (playCount == null) {
                playCount = 0;
            }

            // 累加该用户的所有视频播放量
            videoCountMap.put(userId, videoCountMap.getOrDefault(userId, 0) + playCount);
        }


        for (Map.Entry<String, Integer> entry : videoCountMap.entrySet()) {
            String userId = entry.getKey();     //用户id
            Integer playCount = entry.getValue();  //该用户的总播放量

            // 创建统计信息对象
            StatisticsInfo statisticsInfo = new StatisticsInfo();
            statisticsInfo.setStatisticsDate(statisticsDate);     // 统计日期
            statisticsInfo.setUserId(userId);                    // 用户ID
            statisticsInfo.setDataType(StatisticsTypeEnum.PLAY.getType()); // 数据类型：播放量
            statisticsInfo.setStatisticsCount(playCount);        // 统计数值

            // 添加到统计信息列表中
            statisticsInfoList.add(statisticsInfo);   //统计具体每天每个用户的总播放数量
        }


        //统计粉丝量
        List<StatisticsInfo> fansDataList = this.statisticsInfoMapper.selectStatisticsFans(statisticsDate);
        for (StatisticsInfo statisticsInfo : fansDataList) {
            statisticsInfo.setStatisticsDate(statisticsDate);
            statisticsInfo.setDataType(StatisticsTypeEnum.FANS.getType());
        }
        statisticsInfoList.addAll(fansDataList);


        //统计评论
        List<StatisticsInfo> commentDataList = this.statisticsInfoMapper.selectStatisticsComment(statisticsDate);
        for (StatisticsInfo statisticsInfo : commentDataList) {
            statisticsInfo.setStatisticsDate(statisticsDate);
            statisticsInfo.setDataType(StatisticsTypeEnum.COMMENT.getType());
        }
        statisticsInfoList.addAll(commentDataList);


        //统计 弹幕、点赞、收藏、投币
        List<StatisticsInfo> statisticsInfoOthers = this.statisticsInfoMapper.selectStatisticsInfo(statisticsDate,
                new Integer[]{UserActionTypeEnum.VIDEO_LIKE.getType(), UserActionTypeEnum.VIDEO_COIN.getType(), UserActionTypeEnum.VIDEO_COLLECT.getType()});

        for (StatisticsInfo statisticsInfo : statisticsInfoOthers) {
            statisticsInfo.setStatisticsDate(statisticsDate);
            if (UserActionTypeEnum.VIDEO_LIKE.getType().equals(statisticsInfo.getDataType())) {
                statisticsInfo.setDataType(StatisticsTypeEnum.LIKE.getType());
            } else if (UserActionTypeEnum.VIDEO_COLLECT.getType().equals(statisticsInfo.getDataType())) {
                statisticsInfo.setDataType(StatisticsTypeEnum.COLLECTION.getType());
            } else if (UserActionTypeEnum.VIDEO_COIN.getType().equals(statisticsInfo.getDataType())) {
                statisticsInfo.setDataType(StatisticsTypeEnum.COIN.getType());
            }
        }
        statisticsInfoList.addAll(statisticsInfoOthers);

        this.statisticsInfoMapper.insertOrUpdateBatch(statisticsInfoList);
    }

    @Override
    public Map<String, Integer> getStatisticsInfoActualTime(String userId) {
        Map<String, Integer> result = statisticsInfoMapper.selectTotalCountInfo(userId);
        if (userId!=null) {
            //查询粉丝数
            result.put("userCount", userFocusMapper.selectFansCount(userId));
        } else {
            //后台管理员查询用户数
            result.put("userCount", userInfoMapper.selectCount(new UserInfoQuery()));
        }
        return result;
    }

    @Override
    public List<StatisticsInfo> findListTotalInfoByParam(StatisticsInfoQuery param) {
        return statisticsInfoMapper.selectListTotalInfoByParam(param);
    }

    @Override
    public List<StatisticsInfo> findUserCountTotalInfoByParam(StatisticsInfoQuery param) {
        return statisticsInfoMapper.selectUserCountTotalInfoByParam(param);
    }
}