package com.easylive.web.aspect;

import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.exception.BusinessException;
import com.easylive.redis.RedisUtils;
import com.easylive.web.annotation.GlobalInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
//定义了一个切面
public class GlobalOperationAspect {

    @Resource
    private RedisUtils redisUtils;

    @Before("@annotation(com.easylive.web.annotation.GlobalInterceptor)")
    public void interceptorDoc(JoinPoint joinPoint){
        Method method = ((MethodSignature)joinPoint.getSignature()).getMethod();
        GlobalInterceptor globalInterceptor = method.getAnnotation(GlobalInterceptor.class);

        if(globalInterceptor==null) return;

        if(globalInterceptor.checkLogin()){
            checkLogin();
        }

    }

    private void checkLogin(){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = null;
        
        // 优先从Cookie中获取token（HttpOnly Cookie由浏览器自动携带）
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if(cookie.getName().equals(Constants.TOKEN_WEB)){
                    token = cookie.getValue();
                    break;
                }
            }
        }
        
        // 如果Cookie中没有，再从请求头获取（兼容前端手动发送token的情况）
        if(token == null || token.isEmpty()){
            token = request.getHeader(Constants.TOKEN_WEB);
        }
        
        if(token == null || token.isEmpty()){
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_TOKEN_WEB+token);
        if(tokenUserInfoDto==null){
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        log.info("checkLogin");
    }

}
