package com.dineflow.order;

import com.dineflow.exception.BaseException;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.order.support.OrderPriceCalculator;
import com.dineflow.order.support.SettlementValidation;
import com.dineflow.properties.OrderSettlementProperties;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettlementRulesTest {
    private final OrderPriceCalculator calculator = new OrderPriceCalculator(new OrderSettlementProperties());

    @Test
    void equivalentDecimalScalesAreAccepted() {
        assertEquals(new BigDecimal("20.00"), SettlementValidation.money(new BigDecimal("20.000")));
    }

    @Test
    void invalidMoneyIsRejectedInsteadOfRounded() {
        for (String value : List.of("-0.01", "0.001", "100000000")) {
            BaseException error = assertThrows(BaseException.class,
                    () -> SettlementValidation.money(new BigDecimal(value)));
            assertEquals("INVALID_PARAMETER", error.getErrorCode());
        }
    }

    @Test
    void invalidQuantitiesAreRejected() {
        for (Integer quantity : new Integer[]{null, 0, -1, 10000}) {
            assertThrows(BaseException.class, () -> SettlementValidation.quantity(quantity));
        }
    }

    @Test
    void totalsIncludeAllFeesAndDiscount() {
        SettlementItem item = SettlementItem.builder().number(3).unitPrice(new BigDecimal("10.20")).build();
        assertEquals(new BigDecimal("33.10"), calculator.calculate(List.of(item),
                new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("0.50")).getAmount());
    }

    @Test
    void overflowAndNegativeFinalAmountAreRejected() {
        SettlementItem huge = SettlementItem.builder().number(2).unitPrice(new BigDecimal("99999999.99")).build();
        assertThrows(BaseException.class, () -> calculator.calculate(List.of(huge)));
        SettlementItem small = SettlementItem.builder().number(1).unitPrice(BigDecimal.ONE).build();
        assertThrows(BaseException.class, () -> calculator.calculate(List.of(small), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("2.00")));
    }
}
