package com.dineflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementItemVO {

    private Long cartId;

    private Long cartVersion;

    private Long dishId;

    private Long setmealId;

    private String name;

    private String image;

    private String dishFlavor;

    private String setmealItemsSnapshot;

    private Integer number;

    /**
     * 当前成交候选单价
     */
    private BigDecimal unitPrice;

    /**
     * 小计
     */
    private BigDecimal subtotal;
}