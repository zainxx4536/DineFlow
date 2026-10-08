package com.dineflow.order.support;

import static com.dineflow.constant.BusinessErrorCode.*;
import static com.dineflow.order.support.SettlementErrors.error;

import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.dto.SelectedCartItemDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;

public final class SettlementValidation {
    private SettlementValidation() {}

    public static final int MAX_QUANTITY = 9999;

    public static boolean positive(Long id) {
        return id != null && id > 0;
    }

    public static void quantity(Integer n) {
        if (n == null || n < 1 || n > MAX_QUANTITY) throw error(INVALID_PARAMETER);
    }

    /** DECIMAL(10,2) 边界；20.000 等价于 20.00，不静默舍入有意义的小数。 */
    public static BigDecimal money(BigDecimal n) {
        if (n == null
                || n.signum() < 0
                || n.stripTrailingZeros().scale() > 2
                || n.compareTo(new BigDecimal("99999999.99")) > 0) throw error(INVALID_PARAMETER);
        return n.setScale(2);
    }

    public static void submit(OrdersSubmitDTO dto) {
        if (dto == null
                || !positive(dto.getAddressBookId())
                || !positive(dto.getAddressVersion())
                || dto.getSelectedCartItems() == null
                || dto.getSelectedCartItems().isEmpty()
                || dto.getSelectedCartItems().size() > 100
                || dto.getSettlementToken() == null
                || dto.getSettlementToken().isBlank()
                || dto.getPayMethod() != 1
                || dto.getDeliveryStatus() == null
                || (dto.getDeliveryStatus() != 0 && dto.getDeliveryStatus() != 1)
                || dto.getTablewareStatus() == null
                || (dto.getTablewareStatus() != 0 && dto.getTablewareStatus() != 1)
                || (dto.getRemark() != null && dto.getRemark().length() > 100))
            throw error(INVALID_PARAMETER);
        HashSet<Long> ids = new HashSet<Long>();
        for (SelectedCartItemDTO item : dto.getSelectedCartItems()) {
            if (item == null
                    || !positive(item.getCartItemId())
                    || !positive(item.getCartVersion())
                    || !ids.add(item.getCartItemId())) throw error(INVALID_PARAMETER);
        }
        money(dto.getConfirmedAmount());
        if (dto.getTablewareStatus() == 0
                && (dto.getTablewareNumber() == null
                        || dto.getTablewareNumber() < 0
                        || dto.getTablewareNumber() > MAX_QUANTITY)) throw error(INVALID_PARAMETER);
        if (dto.getDeliveryStatus() == 0
                && (dto.getEstimatedDeliveryTime() == null
                        || !dto.getEstimatedDeliveryTime().isAfter(LocalDateTime.now())))
            throw error(INVALID_PARAMETER);
        if (dto.getDeliveryStatus() == 1) dto.setEstimatedDeliveryTime(null);
    }
}
