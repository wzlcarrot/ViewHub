package com.easylive.service.impl;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.po.UserVideoSeries;
import com.easylive.entity.po.UserVideoSeriesVideo;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.UserVideoSeriesQuery;
import com.easylive.entity.query.UserVideoSeriesVideoQuery;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.UserVideoSeriesMapper;
import com.easylive.mappers.UserVideoSeriesVideoMapper;
import com.easylive.mappers.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import com.easylive.service.UserVideoSeriesService;
import com.easylive.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * 用户视频序列归档 业务接口实现
 */
@Service("userVideoSeriesService")
@RequiredArgsConstructor
public class UserVideoSeriesServiceImpl implements UserVideoSeriesService {

    private final UserVideoSeriesMapper<UserVideoSeries, UserVideoSeriesQuery> userVideoSeriesMapper;

    private final VideoInfoMapper<VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final UserVideoSeriesVideoMapper<UserVideoSeriesVideo, UserVideoSeriesVideoQuery> userVideoSeriesVideoMapper;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserVideoSeries> findListByParam(UserVideoSeriesQuery param) {
        return this.userVideoSeriesMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserVideoSeriesQuery param) {
        return this.userVideoSeriesMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserVideoSeries> findListByPage(UserVideoSeriesQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserVideoSeries> list = this.findListByParam(param);
        PaginationResultVO<UserVideoSeries> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserVideoSeries bean) {
        return this.userVideoSeriesMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserVideoSeries> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userVideoSeriesMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserVideoSeries> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userVideoSeriesMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserVideoSeries bean, UserVideoSeriesQuery param) {
        StringTools.checkParam(param);
        return this.userVideoSeriesMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserVideoSeriesQuery param) {
        StringTools.checkParam(param);
        return this.userVideoSeriesMapper.deleteByParam(param);
    }

    /**
     * 根据SeriesId获取对象
     */
    @Override
    public UserVideoSeries getUserVideoSeriesBySeriesId(Integer seriesId) {
        return this.userVideoSeriesMapper.selectBySeriesId(seriesId);
    }

    /**
     * 根据SeriesId修改
     */
    @Override
    public Integer updateUserVideoSeriesBySeriesId(UserVideoSeries bean, Integer seriesId) {
        return this.userVideoSeriesMapper.updateBySeriesId(bean, seriesId);
    }

    /**
     * 根据SeriesId删除
     */
    @Override
    public Integer deleteUserVideoSeriesBySeriesId(Integer seriesId) {
        return this.userVideoSeriesMapper.deleteBySeriesId(seriesId);
    }

    //保存视频合集
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserVideoSeries(UserVideoSeries bean, String videoIds) {
        //看看seriesId和videoIds是否为空
        if (bean.getSeriesId() == null && StringTools.isEmpty(videoIds)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //表示没有视频合集号，则表示新增视频合集
        if (bean.getSeriesId() == null) {
            //首先进行校验，担心黑客把别人的视频上传到自己的视频合集中
            checkVideoIds(bean.getUserId(), videoIds);
            bean.setUpdateTime(new Date());
            //到后面通过sort字段来进行排序，越小越靠前
            bean.setSort(this.userVideoSeriesMapper.selectMaxSort(bean.getUserId()) + 1);
            this.userVideoSeriesMapper.insert(bean);

            Integer newSeriesId = bean.getSeriesId();
            if (newSeriesId == null) {
                // 如果insert操作没有正确回写ID，需要重新查询获取
                throw new BusinessException("创建视频系列失败，无法获取系列ID");
            }

            //把视频添加到视频合集中的视频列表中
            this.saveSeriesVideo(bean.getUserId(), bean.getSeriesId(), videoIds);
        } else {
            //如果有视频合集号，则表示修改视频合集
            UserVideoSeriesQuery seriesQuery = new UserVideoSeriesQuery();
            seriesQuery.setUserId(bean.getUserId());
            seriesQuery.setSeriesId(bean.getSeriesId());
            this.userVideoSeriesMapper.updateByParam(bean, seriesQuery);
        }
    }

    //校验视频id，，，可能别人把别人的视频添加到自己的视频合集中
    private void checkVideoIds(String userId, String videoIds) {
        String videoIdArray[] = videoIds.split(",");
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setVideoIdArray(videoIdArray);
        videoInfoQuery.setUserId(userId);

        Integer count = videoInfoMapper.selectCount(videoInfoQuery);

        if(count!=videoIdArray.length){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    @Override
    public void changeVideoSeriesSort(String userId, String seriesIds) {
        String[] seriesIdArray = seriesIds.split(",");
        List<UserVideoSeries> videoSeriesList = new ArrayList<>();
        Integer sort = 0;
        for (String seriesId : seriesIdArray) {
            UserVideoSeries videoSeries = new UserVideoSeries();
            videoSeries.setUserId(userId);
            videoSeries.setSeriesId(Integer.parseInt(seriesId));
            videoSeries.setSort(++sort);
            videoSeriesList.add(videoSeries);
        }
        //主要就是对sort进行修改
        userVideoSeriesMapper.changeSort(videoSeriesList);
    }

    //把视频保存到视频合集中的方法
    @Override
    public void saveSeriesVideo(String userId, Integer seriesId, String videoIds) {
        //获取视频合集
        UserVideoSeries userVideoSeries = getUserVideoSeriesBySeriesId(seriesId);
        //判断用户是否是合集的所有者
        if (userVideoSeries==null||!userVideoSeries.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //判断
        checkVideoIds(userId, videoIds);
        String[] videoIdArray = videoIds.split(",");
        Integer sort = this.userVideoSeriesVideoMapper.selectMaxSort(seriesId);
        List<UserVideoSeriesVideo> seriesVideoList = new ArrayList<>();
        for (String videoId : videoIdArray) {
            UserVideoSeriesVideo videoSeriesVideo = new UserVideoSeriesVideo();
            videoSeriesVideo.setVideoId(videoId);
            videoSeriesVideo.setSort(++sort);
            videoSeriesVideo.setSeriesId(seriesId);
            videoSeriesVideo.setUserId(userId);
            seriesVideoList.add(videoSeriesVideo);
        }
        //把视频保存到视频合集 中
        this.userVideoSeriesVideoMapper.insertOrUpdateBatch(seriesVideoList);
    }

    @Override
    public void delSeriesVideo(String userId, Integer seriesId, String videoId) {
        UserVideoSeriesVideoQuery videoSeriesVideoQuery = new UserVideoSeriesVideoQuery();
        videoSeriesVideoQuery.setUserId(userId);
        videoSeriesVideoQuery.setSeriesId(seriesId);
        videoSeriesVideoQuery.setVideoId(videoId);
        this.userVideoSeriesVideoMapper.deleteByParam(videoSeriesVideoQuery);
    }

    @Override
    public List<UserVideoSeries> getUserAllSeries(String userId) {
        return userVideoSeriesMapper.selectUserAllSeries(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delVideoSeries(String userId, Integer seriesId) {
        UserVideoSeriesQuery seriesQuery = new UserVideoSeriesQuery();
        seriesQuery.setUserId(userId);
        seriesQuery.setSeriesId(seriesId);
        //删除合集
        Integer count = userVideoSeriesMapper.deleteByParam(seriesQuery);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserVideoSeriesVideoQuery videoSeriesVideoQuery = new UserVideoSeriesVideoQuery();
        videoSeriesVideoQuery.setSeriesId(seriesId);
        videoSeriesVideoQuery.setUserId(userId);
        //删除合集里面的视频
        userVideoSeriesVideoMapper.deleteByParam(videoSeriesVideoQuery);
    }

    @Override
    public List<UserVideoSeries> findListWithVideoList(UserVideoSeriesQuery query) {
        return userVideoSeriesMapper.selectListWithVideoList(query);
    }

}