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
    @ApiModelProperty("地址簿ID")
    private Long addressBookId;

    @ApiModelProperty("用户确认时的地址版本")
    private Long addressVersion;

    @ApiModelProperty("本次结算的购物车条目")
    private List<SelectedCartItemDTO> selectedCartItems;

    /**
     * 用户最后确认的服务端报价
     * 注意：不是订单金额来源，后端提交时仍然会重新计算。
     */
    @ApiModelProperty("用户最后确认的服务端报价")
    private BigDecimal confirmedAmount;

    @ApiModelProperty("支付方式")
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

}
