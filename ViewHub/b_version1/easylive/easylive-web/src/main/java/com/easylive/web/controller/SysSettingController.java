package com.easylive.web.controller;

import com.easylive.component.RedisComponent;
import com.easylive.entity.vo.ResponseVO;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sysSetting")
@Validated
@RequiredArgsConstructor
//系统设置。。读取一些系统设置信息
public class SysSettingController extends ABaseController {

    private final RedisComponent redisComponent;

    @RequestMapping("/getSetting")

    public ResponseVO getSetting() {
        return getSuccessResponseVO(redisComponent.getSysSettingDto());
    }
}