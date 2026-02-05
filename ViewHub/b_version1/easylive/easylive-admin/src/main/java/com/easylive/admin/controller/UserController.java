package com.easylive.admin.controller;

import com.easylive.entity.query.UserInfoQuery;
import com.easylive.entity.query.VideoCommentQuery;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController extends ABaseController{

    private final UserInfoService userInfoService;

    @RequestMapping("/loadUser")
    public ResponseVO loadUser(UserInfoQuery query){
        query.setOrderBy("join_time desc");

        PaginationResultVO resultVO = userInfoService.findListByPage(query);

        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/changeStatus")
    public ResponseVO changeStatus(String userId,Integer status){
        userInfoService.changeStatus(userId,status);

        return getSuccessResponseVO(null);
    }

}
