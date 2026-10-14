package com.dineflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderSubmitItemDTO implements Serializable {

    // 用户确认的购物车条目ID
    private Long cartId;
    // 菜品ID、套餐ID只填写其中一个
    private Long dishId;
    private Long setmealId;
    // 用户确认的口味，不检查口味配置是否仍有效
    private String dishFlavor;
    // 用户确认的数量
    private Integer number;
    // 用户确认的单价，来自preview返回的currentPrice
    private BigDecimal currentPrice;
    // 用户确认时的购物车条目版本
    private Integer cartVersion;
}