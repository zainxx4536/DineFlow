package com.dineflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
@ApiModel("用户登录时传递的数据模型")
public class UserLoginDTO implements Serializable {
    @ApiModelProperty("微信授权码")
    private String code;
}
