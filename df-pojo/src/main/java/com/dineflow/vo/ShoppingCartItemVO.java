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
public class ShoppingCartItemVO {

    private Long cartId;
    private Long dishId;
    private Long setmealId;

    private String name;
    private String image;
    private String dishFlavor;
    private Integer number;

    // 当前商品单价
    private BigDecimal currentPrice;
    // 当前单价 × 数量
    private BigDecimal subtotal;

    private Boolean available;
    private String unavailableReason;

    // 本次查询时的购物车条目版本
    private Integer cartVersion;
}