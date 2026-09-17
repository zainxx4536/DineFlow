package com.dineflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel("用户下单传递的数据模型")
public class OrdersSubmitDTO implements Serializable {
    @ApiModelProperty("地址簿ID")
    private Long addressBookId;
    @ApiModelProperty("付款方式")
    private int payMethod;
    @ApiModelProperty("备注")
    private String remark;
    @ApiModelProperty("预计送达时间")
    private LocalDateTime estimatedDeliveryTime;
    @ApiModelProperty("配送状态：1立即送出；0选择具体时间")
    private Integer deliveryStatus;
    @ApiModelProperty("餐具数量")
    private Integer tablewareNumber;
    @ApiModelProperty("餐具数量状态：1按餐量提供；0选择具体数量")
    private Integer tablewareStatus;
    @ApiModelProperty("打包费")
    private Integer packAmount;
    @ApiModelProperty("总金额")
    private BigDecimal amount;
}
