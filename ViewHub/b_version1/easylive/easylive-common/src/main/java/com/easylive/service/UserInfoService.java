package com.easylive.service;

import java.util.List;

import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UserCountInfoDto;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.vo.PaginationResultVO;


/**
 * 用户信息 业务接口
 */
public interface UserInfoService {

	/**
	 * 根据条件查询列表
	 */
	List<UserInfo> findListByParam(UserInfoQuery param);

	/**
	 * 根据条件查询列表
	 */
	Integer findCountByParam(UserInfoQuery param);

	/**
	 * 分页查询
	 */
	PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param);

	/**
	 * 新增
	 */
	Integer add(UserInfo bean);

	/**
	 * 批量新增
	 */
	Integer addBatch(List<UserInfo> listBean);

	/**
	 * 批量新增/修改
	 */
	Integer addOrUpdateBatch(List<UserInfo> listBean);

	/**
	 * 多条件更新
	 */
	Integer updateByParam(UserInfo bean,UserInfoQuery param);

	/**
	 * 多条件删除
	 */
	Integer deleteByParam(UserInfoQuery param);

	/**
	 * 根据UserId查询对象
	 */
	UserInfo getUserInfoByUserId(String userId);


	/**
	 * 根据UserId修改
	 */
	Integer updateUserInfoByUserId(UserInfo bean,String userId);


	/**
	 * 根据UserId删除
	 */
	Integer deleteUserInfoByUserId(String userId);


	/**
	 * 根据Email查询对象
	 */
	UserInfo getUserInfoByEmail(String email);


	/**
	 * 根据Email修改
	 */
	Integer updateUserInfoByEmail(UserInfo bean,String email);


	/**
	 * 根据Email删除
	 */
	Integer deleteUserInfoByEmail(String email);


	/**
	 * 根据Nickname查询对象
	 */
	UserInfo getUserInfoByNickname(String nickname);


	/**
	 * 根据Nickname修改
	 */
	Integer updateUserInfoByNickname(UserInfo bean,String nickname);


	/**
	 * 根据Nickname删除
	 */
	Integer deleteUserInfoByNickname(String nickname);


	//注册
	void register(String email,String nickname,String registerPassword);

	//登录
	TokenUserInfoDto login(String email, String password, String ip);

	//获取用户的粉丝信息
	UserInfo  getUserDetailInfo(String currentUserId, String userId);

	void updateUserInfo(UserInfo userInfo, TokenUserInfoDto tokenUserInfoDto);

    UserCountInfoDto getUserCountInfo(String userId);

    void changeStatus(String userId, Integer status);

}