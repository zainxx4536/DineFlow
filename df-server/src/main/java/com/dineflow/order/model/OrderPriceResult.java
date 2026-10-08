package com.dineflow.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单金额计算结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPriceResult {

    /**
     * 商品金额
     */
    private BigDecimal goodsAmount;

    /**
     * 打包费
     */
    private BigDecimal packAmount;

    /**
     * 配送费
     */
    private BigDecimal deliveryFee;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 最终应付金额
     */
    private BigDecimal amount;
}