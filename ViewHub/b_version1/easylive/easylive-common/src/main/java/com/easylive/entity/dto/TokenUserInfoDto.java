package com.easylive.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/*
* 主要用于封装用户登录后的 Token 信息和部分用户数据，通过json数据来返回给前端。
*
* */
//在 JSON 序列化/反序列化时忽略未知字段（即 JSON 中有但类中没有的字段不会报错）。
//常用于前后端字段不一致或接口兼容性处理。
@JsonIgnoreProperties(ignoreUnknown = true)
//序列化化其实就是将Java对象转化成json数据的作用。
//这个类的主要作用是封装信息给redis中，然后通过token来获取用户信息。也可以返回给前端使用

@Data
public class TokenUserInfoDto implements Serializable {

    private static final long serialVersionUID = 9170480547933408839L;
    private String userId;
    private String nickname;
    private String avatar; // 头像
    private long expireAt;
    private String token;

    private Integer fansCount; // 粉丝数
    private Integer currentCoinCount; // 金币数
    private Integer focusCount; // 关注数

}
