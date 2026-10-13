package com.dineflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPreviewVO {

    // 本次报价条目
    private List<ShoppingCartItemVO> items;

    // 商品总金额
    private BigDecimal goodsAmount;

    // 打包费
    private BigDecimal packAmount;

    // 配送费
    private BigDecimal deliveryAmount;

    // 应付总金额
    private BigDecimal payableAmount;

    // 商品总份数
    private Integer totalNumber;
}