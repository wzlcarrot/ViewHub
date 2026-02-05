package com.easylive.admin.controller;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
        Cookie cookie = new Cookie(Constants.TOKEN_ADMIN, token);
        //setmaxAge为-1表示cookie跟随浏览器关闭而关闭。意思就是关闭浏览器就删除cookie。
        cookie.setMaxAge(-1);

        cookie.setPath("/");

        response.addCookie(cookie);

    }

    //获取token对应的用户信息
    protected TokenUserInfoDto getTokenUserInfoDto(){
        //获取请求
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader(Constants.TOKEN_ADMIN);

        return redisComponent.getTokenInfo(token);
    }

    //清理一下cookie就行了
    protected void cleanCookie(HttpServletResponse response){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        //清除上一次的token
        Cookie cookies[] = request.getCookies();
        String token = null;
        if(cookies==null) return;
        for(Cookie cookie: cookies){
            if(cookie.getName().equals(Constants.TOKEN_ADMIN)){
                redisComponent.cleanToken(cookie.getValue());
                cookie.setPath("/");
                cookie.setMaxAge(0);
                //cookie后端是不能直接删除的，，顶多是删除cookie中的token。。。后端通知前端来删除cookie。

                response.addCookie(cookie);
                break;
            }
        }

    }
}
