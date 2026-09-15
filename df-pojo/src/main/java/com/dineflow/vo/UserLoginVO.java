package com.dineflow.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("用户登录时返回的数据模型")
public class UserLoginVO implements Serializable {
    @ApiModelProperty("用户ID")
    private Long id;
    @ApiModelProperty("微信用户OPENID")
    private String openid;
    @ApiModelProperty("JWT 令牌")
    private String token;
}
