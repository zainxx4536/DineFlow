package com.dineflow.order.support;

import static com.dineflow.constant.BusinessErrorCode.INVALID_PARAMETER;
import static com.dineflow.order.support.SettlementErrors.error;

import com.dineflow.order.model.OrderPriceResult;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.properties.OrderSettlementProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Preview 和 Submit 共用同一计价、精度和范围规则。仅计算，不访问数据库或网络。 */
@Component
@RequiredArgsConstructor
public class OrderPriceCalculator {
    private final OrderSettlementProperties properties;

    public OrderPriceResult calculate(List<SettlementItem> items) {
        return calculate(
                items,
                properties.getPackFee(),
                properties.getDeliveryFee(),
                properties.getDiscountAmount());
    }

    public OrderPriceResult calculate(
            List<SettlementItem> items, BigDecimal pack, BigDecimal delivery, BigDecimal discount) {
        if (items == null || items.isEmpty() || items.size() > 100) throw error(INVALID_PARAMETER);
        BigDecimal goods = BigDecimal.ZERO;
        for (SettlementItem item : items) {
            if (item == null) throw error(INVALID_PARAMETER);
            SettlementValidation.quantity(item.getNumber());
            item.setUnitPrice(SettlementValidation.money(item.getUnitPrice()));
            item.setSubtotal(
                    SettlementValidation.money(
                            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getNumber()))));
            goods = goods.add(item.getSubtotal());
        }
        goods = SettlementValidation.money(goods);
        pack = SettlementValidation.money(pack);
        delivery = SettlementValidation.money(delivery);
        discount = SettlementValidation.money(discount);
        BigDecimal amount =
                SettlementValidation.money(goods.add(pack).add(delivery).subtract(discount));
        return OrderPriceResult.builder()
                .goodsAmount(goods)
                .packAmount(pack)
                .deliveryFee(delivery)
                .discountAmount(discount)
                .amount(amount)
                .build();
    }
}
