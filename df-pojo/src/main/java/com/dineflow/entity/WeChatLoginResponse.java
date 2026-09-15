package com.dineflow.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 接收微信登录时微信平台的返回参数
 */
@Data
public class WeChatLoginResponse {

    private String openid;

    @JsonProperty("session_key")
    private String sessionKey;

    private Integer errcode;

    private String errmsg;
}