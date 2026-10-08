package com.dineflow.order.support;

import com.dineflow.order.model.OrderPriceResult;
import com.dineflow.order.model.SettlementItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单结算计算组件
 * 避免 Preview 和真正 Submit 各写一套计算逻辑
 * 只负责金额计算，不查数据库、不读 Redis、不调 HTTP
 */
@Component
public class OrderPriceCalculator {

    public OrderPriceResult calculate(
            List<SettlementItem> items,
            BigDecimal packAmount,
            BigDecimal deliveryFee,
            BigDecimal discountAmount) {

        BigDecimal goodsAmount = items.stream()
                .map(item -> item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal amount = goodsAmount
                .add(packAmount)
                .add(deliveryFee)
                .subtract(discountAmount);

        return OrderPriceResult.builder()
                .goodsAmount(goodsAmount)
                .packAmount(packAmount)
                .deliveryFee(deliveryFee)
                .discountAmount(discountAmount)
                .amount(amount)
                .build();
    }

}
