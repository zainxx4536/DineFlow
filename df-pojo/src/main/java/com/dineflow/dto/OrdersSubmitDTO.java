package com.dineflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel("用户下单传递的数据模型")
public class OrdersSubmitDTO implements Serializable {

    @ApiModelProperty("同一次下单的稳定请求号，标准UUID")
    private String requestId;

    private Long addressBookId;

    private Integer addressVersion;

    private int payMethod;

    private String remark;

    private LocalDateTime estimatedDeliveryTime;

    private Integer deliveryStatus;

    private Integer tablewareNumber;

    private Integer tablewareStatus;

    @ApiModelProperty("用户确认的应付总金额")
    private BigDecimal amount;

    @ApiModelProperty("用户确认的商品条目")
    private List<OrderSubmitItemDTO> items;
}