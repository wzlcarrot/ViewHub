package com.easylive.web.controller;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.utils.StringTools;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yaml.snakeyaml.scanner.Constant;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class ABaseController {

    protected static final String STATUC_SUCCESS = "success";

    protected static final String STATUC_ERROR = "error";

    @Resource
    private RedisComponent redisComponent;

    //返回成功，t是成功返回的数据对象
    protected <T> ResponseVO getSuccessResponseVO(T t) {
        ResponseVO<T> responseVO = new ResponseVO<>();
        responseVO.setStatus(STATUC_SUCCESS);
        responseVO.setCode(ResponseCodeEnum.CODE_200.getCode());
        responseVO.setInfo(ResponseCodeEnum.CODE_200.getMsg());
        responseVO.setData(t);
        return responseVO;
    }

    protected <T> ResponseVO getBusinessErrorResponseVO(BusinessException e, T t) {
        ResponseVO vo = new ResponseVO();
        vo.setStatus(STATUC_ERROR);
        if (e.getCode() == null) {
            vo.setCode(ResponseCodeEnum.CODE_600.getCode());
        } else {
            vo.setCode(e.getCode());
        }
        vo.setInfo(e.getMessage());
        vo.setData(t);
        return vo;
    }

    protected <T> ResponseVO getServerErrorResponseVO(T t) {
        ResponseVO vo = new ResponseVO();
        vo.setStatus(STATUC_ERROR);
        vo.setCode(ResponseCodeEnum.CODE_500.getCode());
        vo.setInfo(ResponseCodeEnum.CODE_500.getMsg());
        vo.setData(t);
        return vo;
    }

    //获取ip地址
    /*
    * getIpAddr() 放在 ABaseController 中是为了让多个 Controller 共享这个通用方法，
    * 统一处理与请求相关的 IP 获取逻辑，是合理且推荐的做法
    * */
    protected String getIpAddr() {
        //客户端到服务器端发送我好帅。。request里面保存了
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        //
        String ip = request.getHeader("x-forwarded-for");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            if (ip.indexOf(",") != -1) {
                ip = ip.split(",")[0];
            }
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    //保存token到cookie中
    protected void saveToken2Cookie(HttpServletResponse response,String token){
        // 通过响应头手动设置Cookie，这样可以完全控制所有属性包括SameSite
        // SameSite=Lax 允许同站和顶级导航请求携带Cookie（适合前后端同域或子域的情况）
        // 如果前后端跨域且使用HTTPS，可以改为 SameSite=None; Secure
        // 注意：只使用响应头方式，避免重复设置Cookie
        // HttpOnly 设置为 true，防止XSS攻击通过JavaScript访问Cookie
        String cookieHeader = String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                Constants.TOKEN_WEB, token, Constants.REDIS_KEY_EXPIRE_ONE_DAY*7);
        response.addHeader("Set-Cookie", cookieHeader);
    }

    //从http请求头或Cookie中获取token对应的用户信息
    protected TokenUserInfoDto getTokenUserInfoDto(){
        //获取请求
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
            return null;
        }

        TokenUserInfoDto tokenUserInfoDto = redisComponent.getTokenInfo(token);
        // 如果token无效或已过期，返回null
        if(tokenUserInfoDto == null){
            return null;
        }
        if(tokenUserInfoDto.getExpireAt() < System.currentTimeMillis()){
            return null;
        }
        
        return tokenUserInfoDto;
    }

    //清理一下cookie就行了
    protected void cleanCookie(HttpServletResponse response){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        //清除上一次的token
        Cookie cookies[] = request.getCookies();
        String token = null;
        if(cookies==null) return;
        for(Cookie cookie: cookies){
            if(cookie.getName().equals(Constants.TOKEN_WEB)){
                redisComponent.cleanToken(cookie.getValue());
                cookie.setPath("/");
                cookie.setMaxAge(0);
                //设置HttpOnly，防止XSS攻击通过JavaScript访问Cookie
                cookie.setHttpOnly(true);
                //设置Secure，只在HTTPS连接下传输Cookie（如果项目使用HTTPS，取消注释此行）
                // cookie.setSecure(true);
                //cookie后端是不能直接删除的，，顶多是删除cookie中的token。。。后端通知前端来删除cookie。

                response.addCookie(cookie);
                break;
            }
        }

    }

    //从cookie中获取token对应的用户信息
    public TokenUserInfoDto getTokenInfoFromCookie() {
        // 获取当前请求对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        // 获取cookie中的token
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String token = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equalsIgnoreCase(Constants.TOKEN_WEB)) {
                token = cookie.getValue();
                break;
            }
        }
        // 如果没有找到token，返回null
        if (token == null) {
            return null;
        }
        // 通过token获取用户信息
        return redisComponent.getTokenInfo(token);
    }
}
