package com.dineflow.dto;

import lombok.Data;

@Data
public class SelectedCartItemDTO {

    /**
     * 购物车条目主键
     * 对应 shopping_cart.id
     */
    private Long cartItemId;

    /**
     * 用户确认时该购物车条目的版本
     */
    private Long cartVersion;
}