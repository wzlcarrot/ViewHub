package com.easylive.service.impl;

import lombok.RequiredArgsConstructor;
import java.util.Date;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import com.easylive.cache.ThreeLevelCacheManager;
import com.easylive.component.RedisComponent;
import com.easylive.redis.BloomFilterComponent;
import com.easylive.redis.RedisUtils;
import com.easylive.entity.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import com.easylive.entity.dto.CountInfoDto;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UserCountInfoDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.enums.UserSexEnum;
import com.easylive.entity.enums.UserStatusEnum;
import com.easylive.entity.po.UserFocus;
import com.easylive.entity.query.UserFocusQuery;
import com.easylive.exception.BusinessException;
import com.easylive.mappers.UserFocusMapper;
import com.easylive.service.UserFocusService;
import com.easylive.utils.CopyTools;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.query.SimplePage;
import com.easylive.mappers.UserInfoMapper;
import com.easylive.service.UserInfoService;
import com.easylive.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;


/**
 * 用户信息 业务接口实现
 */
@Service("userInfoService")
@Slf4j
@RequiredArgsConstructor
public class UserInfoServiceImpl implements UserInfoService {

	private final UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	private final UserFocusMapper<UserFocus, UserFocusQuery> userFocusMapper;

	private final RedisComponent redisComponent;

	private final ThreeLevelCacheManager threeLevelCacheManager;

	private final BloomFilterComponent bloomFilterComponent;

	private final RedisUtils<Object> redisUtils;

	/**
	 * 用户信息缓存键前缀
	 */
	private static final String CACHE_KEY_PREFIX_USER = "user_info:";

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<UserInfo> findListByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<UserInfo> list = this.findListByParam(param);
		PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(UserInfo bean) {
		return this.userInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(UserInfo bean, UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据UserId获取对象（使用三级缓存）
	 * 场景：用户信息查询频繁，适合缓存
	 * 结合布隆过滤器防止缓存穿透
	 */
	@Override
	public UserInfo getUserInfoByUserId(String userId) {
		String cacheKey = CACHE_KEY_PREFIX_USER + userId;

		// 布隆过滤器未命中时，容错一次数据库查询并回填布隆与缓存
		if (!bloomFilterComponent.mightContainUser(userId)) {
			log.debug("布隆过滤器判定用户不存在，尝试回源校验，userId: {}", userId);
			UserInfo dbInfo = this.userInfoMapper.selectByUserId(userId);
			bloomFilterComponent.addUser(userId);
			if (dbInfo != null) {
				threeLevelCacheManager.put(cacheKey, dbInfo);
			}
			return dbInfo;
		}

		return threeLevelCacheManager.get(cacheKey, key -> {
			log.info("从MySQL加载用户信息，userId: {}", userId);
			UserInfo info = this.userInfoMapper.selectByUserId(userId);
			bloomFilterComponent.addUser(userId);
			return info;
		});
	}

	/**
	 * 根据UserId修改（更新时删除缓存）
	 */
	@Override
	public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
		Integer result = this.userInfoMapper.updateByUserId(bean, userId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_USER + userId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("更新用户信息，删除缓存，userId: {}", userId);
		}
		return result;
	}

	/**
	 * 根据UserId删除（删除时删除缓存）
	 */
	@Override
	public Integer deleteUserInfoByUserId(String userId) {
		Integer result = this.userInfoMapper.deleteByUserId(userId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_USER + userId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("删除用户信息，删除缓存，userId: {}", userId);
		}
		return result;
	}

	/**
	 * 根据Email获取对象
	 */
	@Override
	public UserInfo getUserInfoByEmail(String email) {
		return this.userInfoMapper.selectByEmail(email);
	}

	/**
	 * 根据Email修改
	 */
	@Override
	public Integer updateUserInfoByEmail(UserInfo bean, String email) {
		return this.userInfoMapper.updateByEmail(bean, email);
	}

	/**
	 * 根据Email删除
	 */
	@Override
	public Integer deleteUserInfoByEmail(String email) {
		return this.userInfoMapper.deleteByEmail(email);
	}

	/**
	 * 根据Nickname获取对象
	 */
	@Override
	public UserInfo getUserInfoByNickname(String nickname) {
		return this.userInfoMapper.selectByNickname(nickname);
	}

	/**
	 * 根据Nickname修改
	 */
	@Override
	public Integer updateUserInfoByNickname(UserInfo bean, String nickname) {
		return this.userInfoMapper.updateByNickname(bean, nickname);
	}

	/**
	 * 根据Nickname删除
	 */
	@Override
	public Integer deleteUserInfoByNickname(String nickname) {
		return this.userInfoMapper.deleteByNickname(nickname);
	}

	//通过输入的进行进行注册，执行相应的查询语句
	@Override
	public void register(String email, String nickname, String registerPassword) {
		// 判断邮箱是否已经存在
		UserInfo userInfo = userInfoMapper.selectByEmail(email);
		if(userInfo != null){
			/*
			* 我可以这样理解吗。。就是跑出异常对象，在全局异常类汇总进行捕获相应的对象。
			* 当捕获的对象匹配相应的异常类型。则执行相关的语句。
			* */
			throw new BusinessException("邮箱已经存在");
		}

		UserInfo nickNameUser = userInfoMapper.selectByNickname(nickname);
		if(nickNameUser != null){
			throw new BusinessException("昵称已经存在");
		}
		userInfo = new UserInfo();
		String userId = StringTools.getRandomNumber(Constants.length_10);
		userInfo.setUserId(userId);
		userInfo.setNickName(nickname);
		userInfo.setPassword(StringTools.encodeByMd5(registerPassword));
		userInfo.setJoinTime(new Date());
		userInfo.setEmail( email);
		userInfo.setStatus(UserStatusEnum.ENABLED.getStatus());
		userInfo.setSex(UserSexEnum.SECRECY.getType());
		userInfo.setTheme(Constants.ONE);
		userInfo.setAvatar("");


		SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
		userInfo.setTotalCoinCount(sysSettingDto.getRegisterCoinCount());
		userInfo.setCurrentCoinCount(sysSettingDto.getRegisterCoinCount());
		userInfoMapper.insert(userInfo);
	}

	//实现用户登录
	public TokenUserInfoDto login(String email, String password, String ip){
		UserInfo userInfo = userInfoMapper.selectByEmail(email);

		if(userInfo==null||userInfo.getPassword().equals(password)==false){
			throw new BusinessException("用户不存在或者密码错误");
		}

		//如果当前用户状态为禁用
		if(UserStatusEnum.DISABLED.getStatus().equals(userInfo.getStatus())){
			throw new BusinessException("用户被禁用");
		}

		//更新用户最后一次登录时间和用户ip
		UserInfo updateUserInfo = new UserInfo();
		updateUserInfo.setLastLoginTime(new Date());
		updateUserInfo.setLastLoginIp(ip);
		userInfoMapper.updateByUserId(updateUserInfo, userInfo.getUserId());
		//把userInfo的信息复制给tokenUserInfoDto
		TokenUserInfoDto tokenUserInfoDto = CopyTools.copy(userInfo, TokenUserInfoDto.class);
		redisComponent.saveTokenInfo(tokenUserInfoDto);

		return tokenUserInfoDto;
	}

	@Override
	public UserInfo getUserDetailInfo(String currentUserId, String userId) {

		UserInfo userInfo = getUserInfoByUserId(userId);
		if(userInfo==null){
			throw new BusinessException(ResponseCodeEnum.CODE_404);
		}
		CountInfoDto countInfoDto = userInfoMapper.selectSumCountInfo(userId);

		//获赞数和播放数导入到userInfo中
		CopyTools.copyProperties(countInfoDto, userInfo);
		System.out.println("countInfoDto:"+countInfoDto);
		
		// 优化：优先从Redis Set获取关注数和粉丝数（O(1)复杂度）
		String followingsKey = Constants.REDIS_KEY_FOLLOWINGS_PREFIX + userId;
		String followersKey = Constants.REDIS_KEY_FOLLOWERS_PREFIX + userId;
		
		long focusCount = redisUtils.sCard(followingsKey);
		long fansCount = redisUtils.sCard(followersKey);
		
		// 如果Redis中没有（可能是新用户或缓存未命中），回源数据库
		if (focusCount == 0) {
			Integer dbFocusCount = userFocusMapper.selectFocusCount(userId);
			focusCount = dbFocusCount != null ? dbFocusCount : 0;
		}
		if (fansCount == 0) {
			Integer dbFansCount = userFocusMapper.selectFansCount(userId);
			fansCount = dbFansCount != null ? dbFansCount : 0;
		}
		
		userInfo.setFansCount((int) fansCount);
		userInfo.setFocusCount((int) focusCount);

		//currentUserId的意思是当前登录用户，如果为空，则没有关注。如果不为空，则查看当前用户是否关注了该用户
		if (currentUserId == null) {
			userInfo.setHaveFocus(false);
		} else {
			UserFocus userFocus = userFocusMapper.selectByUserIdAndFocusUserId(currentUserId, userId);
			userInfo.setHaveFocus(userFocus == null ? false : true);
		}

		return userInfo;
	}

	@Override
	@Transactional
	public void updateUserInfo(UserInfo userInfo, TokenUserInfoDto tokenUserInfoDto) {
		//dbinfo是修改信息后的 数据，tokenuserinfoDto是修改前的信息
		UserInfo dbInfo = userInfoMapper.selectByUserId(userInfo.getUserId());

		if(!dbInfo.getNickName().equals(userInfo.getNickName())&&dbInfo.getCurrentCoinCount()<Constants.UPDATE_NICK_NAME_COIN){
			throw new BusinessException("用户的硬币不足，无法修改昵称");
		}

		if(!dbInfo.getNickName().equals(userInfo.getNickName())){
			Integer count = userInfoMapper.updateCoinCountInfo(userInfo.getUserId(),-Constants.UPDATE_NICK_NAME_COIN);
			if(count==0){
				throw new BusinessException("用户的硬币不足，无法修改昵称");
			}

		}

		userInfoMapper.updateByUserId(userInfo, userInfo.getUserId());

		// 更新用户信息后，删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX_USER + userInfo.getUserId();
		threeLevelCacheManager.evict(cacheKey);
		log.info("更新用户信息，删除缓存，userId: {}", userInfo.getUserId());

		//tokenuserinfoDto和redis里面的信息也要进行相应的修改
		Boolean updateTokenInfo = false;
		if (userInfo.getAvatar()!=null && tokenUserInfoDto.getAvatar() != null &&!userInfo.getAvatar().equals(tokenUserInfoDto.getAvatar())) {
			tokenUserInfoDto.setAvatar(userInfo.getAvatar());
			updateTokenInfo = true;
		}
		if (userInfo.getNickName()!=null && tokenUserInfoDto.getNickname() != null &&!tokenUserInfoDto.getNickname().equals(userInfo.getNickName())) {
			tokenUserInfoDto.setNickname(userInfo.getNickName());
			updateTokenInfo = true;
		}
		if (updateTokenInfo) {
			redisComponent.updateTokenInfo(tokenUserInfoDto);
		}

	}

	@Override
	public UserCountInfoDto getUserCountInfo(String userId) {
		UserInfo userInfo = getUserInfoByUserId(userId);
		
		// 优化：优先从Redis Set获取关注数和粉丝数（O(1)复杂度）
		String followingsKey = Constants.REDIS_KEY_FOLLOWINGS_PREFIX + userId;
		String followersKey = Constants.REDIS_KEY_FOLLOWERS_PREFIX + userId;
		
		long focusCount = redisUtils.sCard(followingsKey);
		long fansCount = redisUtils.sCard(followersKey);
		
		// 如果Redis中没有，回源数据库
		if (focusCount == 0) {
			Integer dbFocusCount = userFocusMapper.selectFocusCount(userId);
			focusCount = dbFocusCount != null ? dbFocusCount : 0;
		}
		if (fansCount == 0) {
			Integer dbFansCount = userFocusMapper.selectFansCount(userId);
			fansCount = dbFansCount != null ? dbFansCount : 0;
		}

		UserCountInfoDto countInfoDto = new UserCountInfoDto();

		countInfoDto.setFansCount((int) fansCount);
		countInfoDto.setFocusCount((int) focusCount);
		countInfoDto.setCurrentCoinCount(userInfo.getCurrentCoinCount());
		return countInfoDto;
	}

	public void changeStatus(String userId, Integer status) {
		UserInfo userInfo = new UserInfo();
		userInfo.setStatus(status);
		userInfoMapper.updateByUserId(userInfo, userId);
		// 更新用户状态后，删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX_USER + userId;
		threeLevelCacheManager.evict(cacheKey);
		log.info("更新用户状态，删除缓存，userId: {}", userId);
	}

}