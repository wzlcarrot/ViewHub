package com.easylive.admin.controller;


import com.easylive.component.RedisComponent;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.ws.Response;

@RestController
@RequestMapping("/setting")
@Validated
@Slf4j
@RequiredArgsConstructor
public class SettingController extends ABaseController{

    private final RedisComponent redisComponent;

    private final UserInfoService userInfoService;

    @RequestMapping("/getSetting")
    public ResponseVO getSetting(){
        return getSuccessResponseVO(redisComponent.getSysSettingDto());
    }

    @RequestMapping("/saveSetting")
    public ResponseVO saveSetting(SysSettingDto sysSettingDto){
        redisComponent.saveSetting(sysSettingDto);
        return getSuccessResponseVO(null);
    }

}
