package com.easylive.admin.controller;

import com.easylive.component.RedisComponent;
import com.easylive.entity.config.AppConfig;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.service.UserInfoService;
import com.easylive.utils.StringTools;
import com.easylive.admin.controller.ABaseController;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户信息 Controller
 */
@RestController
@RequestMapping("/account/")
@Validated
@RequiredArgsConstructor
public class AccountController extends ABaseController {

	private final RedisComponent redisComponent;

	private final AppConfig appConfig;

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


	@RequestMapping("/login")
	/*
	HttpServletResponse response
	* 就是服务器端处理相关的请求后然后发送到客户端
	* */
	public ResponseVO login(HttpServletRequest request,HttpServletResponse response,
							@NotEmpty @Email String account,
							@NotEmpty String password,
							@NotEmpty String checkCodeKey,
							@NotEmpty String checkCode){

		try{
			//如果输入的验证码和缓存中的验证码不一致，则抛出异常
			if(checkCode.equalsIgnoreCase(redisComponent.getCheckCode(checkCodeKey))==false){
				throw new BusinessException("图片验证码错误");
			}
			//读配置文件来获取account和password
			if(account.equals(appConfig.getAdminAccount())==false||password.equals(StringTools.encodeByMd5(appConfig.getAdminPassword()))==false){
				throw new BusinessException("用户名或密码错误");
			}

			String token = redisComponent.saveTokenInfo4Admin(account);

			saveToken2Cookie(response,token);
			//登录成功返回用户信息
			return getSuccessResponseVO(account);
		}finally {
			//删除验证码
			redisComponent.cleanCheckCode(checkCodeKey);

			//清除上一次的token
			Cookie cookies[] = request.getCookies();
			String token = null;
			if(cookies!=null){
				for(Cookie cookie: cookies){
					if(cookie.getName().equals(Constants.TOKEN_WEB)){
						token = cookie.getValue();
					}
				}

				if(StringTools.isEmpty(token)!= true){
					redisComponent.cleanToken4Admin(token);
				}
			}
		}

	}


	@RequestMapping("/Logout")
	/*
		实现注销
	* */
	public ResponseVO Logout(HttpServletResponse response) {
		cleanCookie( response);

		return getSuccessResponseVO(null);
	}


}