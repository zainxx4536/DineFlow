package com.dineflow.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 结算对象模型
 * 可以是菜品或者套餐，用于统一计算口径
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementItem {

    private Long cartId;

    private Long cartVersion;

    private Long dishId;

    private Long setmealId;

    private String name;

    private String image;

    private String dishFlavor;

    private Integer number;

    /**
     * 当前服务端读取到的成交单价
     */
    private BigDecimal unitPrice;

    /**
     * 小计 = unitPrice * number
     */
    private BigDecimal subtotal;

    /**
     * 套餐组成成交快照，普通菜品为空
     */
    private String setmealItemsSnapshot;
}