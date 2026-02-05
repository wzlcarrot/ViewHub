package com.easylive.web.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UserCountInfoDto;
import com.easylive.entity.enums.UserStatusEnum;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.redis.RedisConfig;
import com.easylive.redis.RedisUtils;
import com.easylive.service.UserInfoService;
import com.easylive.utils.CopyTools;
import com.easylive.utils.StringTools;
import com.easylive.web.annotation.GlobalInterceptor;
import com.easylive.web.controller.ABaseController;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.*;

/**
 * 用户信息 Controller
 */
@RestController
@RequestMapping("/account")
@Validated
@RequiredArgsConstructor
@Slf4j
public class AccountController extends ABaseController {

	private final UserInfoService userInfoService;

	//用来操控redis数据库的组件
	private final RedisComponent redisComponent;

	@RequestMapping("/checkCode")
	public ResponseVO checkCode() {
		//设置验证码宽高
		ArithmeticCaptcha captcha = new ArithmeticCaptcha(100, 42);
		String code = captcha.text();
		String checkCodeKey = redisComponent.saveCheckCode(code); //保存验证码，得到redis中的键

		String base64 = captcha.toBase64();  //转换成base64格式

		Map<String,String> result = new HashMap<>();
		result.put("checkCode",base64);  //前端用于显示图片
		//前端拿到 key ✔️ 提交时带着 key 和用户输入的验证码 ✔️ 后端用 key 去 Redis 查真实值 ✔️ 然后比对用户输入的是否正确
		result.put("checkCodeKey",checkCodeKey);
		return getSuccessResponseVO(result);
	}



	//注册,并且进行了参数校验
	@RequestMapping("/register")
	public ResponseVO register(@NotEmpty @Email @Size(max = 150) String email,
							   @NotEmpty @Size(max = 20) String nickName,
							   //pattern注解的作用是通过正则表达式来校验
							   @NotEmpty @Pattern(regexp = Constants.REGEX_PASSWORD) String registerPassword,
							   @NotEmpty String checkCodeKey,
							   @NotEmpty String checkCode){

		try{
			//如果输入的验证码和缓存中的验证码不一致，则抛出异常
			if(checkCode.equalsIgnoreCase(redisComponent.getCheckCode(checkCodeKey))==false){
				throw new BusinessException("图片验证码错误");
			}
			//进行注册操作
			userInfoService.register(email,nickName,registerPassword);
			return getSuccessResponseVO(null);
		}finally {
			//删除验证码
			redisComponent.cleanCheckCode(checkCodeKey);
		}
	}

	@RequestMapping("/login")
	/*
	HttpServletResponse response
	* 就是服务器端处理相关的请求后然后发送到客户端
	* */
	public ResponseVO login(HttpServletRequest request,HttpServletResponse response,
							@NotEmpty @Email String email,
							@NotEmpty String password,
							@NotEmpty String checkCodeKey,
							@NotEmpty String checkCode){

		try{
			//如果输入的验证码和缓存中的验证码不一致，则抛出异常
			if(checkCode.equalsIgnoreCase(redisComponent.getCheckCode(checkCodeKey))==false){
				throw new BusinessException("图片验证码错误");
			}
			//获取ip，也就是更新最后的用户登录ip
			String ip = getIpAddr();
			//登录成功，拿到了token。
			TokenUserInfoDto tokenUserInfoDto = userInfoService.login(email, password, ip);

			//token最终保存在cookie中
			saveToken2Cookie(response,tokenUserInfoDto.getToken());

			//设置粉丝数，关注数，硬币数


			//登录成功返回用户信息
			return getSuccessResponseVO(tokenUserInfoDto);
		}finally {
			//删除验证码
			redisComponent.cleanCheckCode(checkCodeKey);

			//清除上一次的token
			Cookie cookies[] = request.getCookies();

			if(cookies!=null){
				String token = null;
				for(Cookie cookie: cookies){
					if(cookie.getName().equals(Constants.TOKEN_WEB)){
						token = cookie.getValue();
					}
				}
				if(StringTools.isEmpty(token)!= true){
					redisComponent.cleanToken(token);
				}
			}
		}


	}


	@RequestMapping("/autoLogin")
	/*
		实现自动登录
	* */

	public ResponseVO autoLogin(HttpServletResponse response) {
		TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

		//如果没有token或token无效
		if(tokenUserInfoDto == null){
			log.info("自动登录失败：未找到有效的token");
			return getSuccessResponseVO(null);
		}

		//如果token即将过期（小于1天），刷新token和Redis中的信息
		long expireTime = tokenUserInfoDto.getExpireAt() - System.currentTimeMillis();
		if(expireTime < Constants.REDIS_KEY_EXPIRE_ONE_DAY){
			log.info("token即将过期，刷新token，剩余时间：{}ms", expireTime);
			// 更新过期时间
			tokenUserInfoDto.setExpireAt(System.currentTimeMillis() + Constants.REDIS_KEY_EXPIRE_ONE_DAY * 7);
			// 刷新HttpOnly Cookie
			saveToken2Cookie(response, tokenUserInfoDto.getToken());
			redisComponent.saveTokenInfo(tokenUserInfoDto);
		}

		log.info("自动登录成功，用户ID：{}", tokenUserInfoDto.getUserId());
		return getSuccessResponseVO(tokenUserInfoDto);
	}

	@RequestMapping("/logout")
	public ResponseVO Logout(HttpServletResponse response) {
		cleanCookie( response);

		return getSuccessResponseVO(null);
	}



	//关注数+粉丝数+硬币数展示
	@RequestMapping("/getUserCountInfo")
	@GlobalInterceptor(checkLogin = true)
	public ResponseVO getUserCountInfo(){
		TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();

		UserCountInfoDto userCountInfoDto = userInfoService.getUserCountInfo(tokenUserInfoDto.getUserId());

		return getSuccessResponseVO(userCountInfoDto);
	}
}