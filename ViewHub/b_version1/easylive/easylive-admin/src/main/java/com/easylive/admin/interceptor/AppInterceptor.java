package com.easylive.admin.interceptor;

import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.exception.BusinessException;
import com.easylive.utils.StringTools;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AppInterceptor implements HandlerInterceptor {

    private final static String URL_ACCOUNT = "/account";
    private final static String URL_FILE = "/file";

    private final RedisComponent redisComponent;

    @Override
    /*
    执行时机：在请求到达Controller之前执行
    返回true表示继续执行后续操作（放行请求）
    返回false表示中断请求处理，不再继续执行
    request：HTTP请求对象
    response：HTTP响应对象
    handler：将要执行的处理器（Controller中的方法）
    * */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        //一个判断。。请求方面的token异常。。一个判断redis也就是后端方面的会话异常。。


        //如果方法为空，则中断请求，进行拦截
        if(handler==null){
            return false;
        }

        //就是handler不是控制器的方法。则放行。HandlerMethod存放的是控制器的方法
        if(handler instanceof HandlerMethod==true){
            return true;
        }

        if(request.getRequestURI().contains(URL_ACCOUNT)){
            return true;
        }
        //从请求头中获取管理员token
        String token = request.getHeader(Constants.TOKEN_ADMIN);

        // 如果是文件请求，从 Cookie 中获取 token
        if(request.getRequestURI().contains(URL_FILE)){
            token = getTokenFromCookie(request);
        }
        // 如果 token 为空，抛出未登录或者登录超时
        if(StringTools.isEmpty(token)){
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        //从redis中获取管理员token信息
        Object sessionObj = redisComponent.getTokenInfo4Admin(token);
        System.out.println("sessionObj:"+sessionObj);
        //如果会话信息不存在，则抛出未登录或者登录超时
        if(sessionObj==null){
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }
        return true;
    }

    private  String getTokenFromCookie(HttpServletRequest request){
        Cookie[] cookies = request.getCookies();
        if(cookies==null){
            return null;
        }
        for(Cookie cookie: cookies){
            if(cookie.getName().equalsIgnoreCase(Constants.TOKEN_ADMIN)){
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
