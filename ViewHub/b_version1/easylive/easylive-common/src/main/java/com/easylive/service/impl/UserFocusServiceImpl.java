package com.easylive.service.impl;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.PageSize;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.po.UserFocus;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.query.SimplePage;
import com.easylive.entity.query.UserFocusQuery;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.UserFocusMapper;
import com.easylive.mappers.UserInfoMapper;
import com.easylive.redis.RedisUtils;
import lombok.RequiredArgsConstructor;
import com.easylive.service.UserFocusService;
import com.easylive.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;


/**
 * 粉丝，关注表 业务接口实现
 */
@Service("userFocusService")
@RequiredArgsConstructor
public class UserFocusServiceImpl implements UserFocusService {

    private final UserFocusMapper<UserFocus, UserFocusQuery> userFocusMapper;

    private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    private final RedisUtils<Object> redisUtils;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserFocus> findListByParam(UserFocusQuery param) {
        return this.userFocusMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserFocusQuery param) {
        return this.userFocusMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserFocus> findListByPage(UserFocusQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserFocus> list = this.findListByParam(param);
        PaginationResultVO<UserFocus> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserFocus bean) {
        return this.userFocusMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserFocus> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userFocusMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserFocus> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userFocusMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserFocus bean, UserFocusQuery param) {
        StringTools.checkParam(param);
        return this.userFocusMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserFocusQuery param) {
        StringTools.checkParam(param);
        return this.userFocusMapper.deleteByParam(param);
    }

    /**
     * 根据UserIdAndFocusUserId获取对象
     */
    @Override
    public UserFocus getUserFocusByUserIdAndFocusUserId(String userId, String focusUserId) {
        return this.userFocusMapper.selectByUserIdAndFocusUserId(userId, focusUserId);
    }

    /**
     * 根据UserIdAndFocusUserId修改
     */
    @Override
    public Integer updateUserFocusByUserIdAndFocusUserId(UserFocus bean, String userId, String focusUserId) {
        return this.userFocusMapper.updateByUserIdAndFocusUserId(bean, userId, focusUserId);
    }

    /**
     * 根据UserIdAndFocusUserId删除
     */
    @Override
    public Integer deleteUserFocusByUserIdAndFocusUserId(String userId, String focusUserId) {
        return this.userFocusMapper.deleteByUserIdAndFocusUserId(userId, focusUserId);
    }

    @Override
    //userid是自己，focusUserId是别人
    public void focusUser(String userId, String focusUserId) {
        if (userId.equals(focusUserId)) {
            throw new BusinessException("不能对自己进行此操作");
        }

        String followingsKey = Constants.REDIS_KEY_FOLLOWINGS_PREFIX + userId;
        String followersKey = Constants.REDIS_KEY_FOLLOWERS_PREFIX + focusUserId;

        // 优先使用 Redis Set 判重
        boolean alreadyFollow = redisUtils.sIsMember(followingsKey, focusUserId);
        if (!alreadyFollow) {
            // 回源 DB 兜底
            UserFocus dbInfo = this.userFocusMapper.selectByUserIdAndFocusUserId(userId, focusUserId);
            if (dbInfo != null) {
                alreadyFollow = true;
            }
        }
        if (alreadyFollow) {
            return;
        }

        UserInfo userInfo = userInfoMapper.selectByUserId(focusUserId);
        if (userInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //如果没有关注，则新增
        UserFocus focus = new UserFocus();
        focus.setUserId(userId);
        focus.setFocusUserId(focusUserId);
        focus.setFocusTime(new Date());
        this.userFocusMapper.insert(focus);

        // 写入关系缓存（镜像 follow / fan 集合）
        redisUtils.sAdd(followingsKey, focusUserId, null);
        redisUtils.sAdd(followersKey, userId, null);
    }

    //取消关注
    @Override
    public void cancelFocus(String userId, String focusUserId) {
        userFocusMapper.deleteByUserIdAndFocusUserId(userId, focusUserId);

        String followingsKey = Constants.REDIS_KEY_FOLLOWINGS_PREFIX + userId;
        String followersKey = Constants.REDIS_KEY_FOLLOWERS_PREFIX + focusUserId;
        redisUtils.sRem(followingsKey, focusUserId);
        redisUtils.sRem(followersKey, userId);
    }
}