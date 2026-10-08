package com.dineflow.order.support;

import com.dineflow.constant.BusinessErrorCode;
import com.dineflow.constant.MessageConstant;
import com.dineflow.exception.BaseException;

/** 结算域统一异常出口，避免页面依赖中文文案。 */
public final class SettlementErrors {
    private SettlementErrors() {}
    public static BaseException error(BusinessErrorCode code) {
        String message = switch (code) {
            case INVALID_PARAMETER -> MessageConstant.INVALID_PARAMETER;
            case USER_NOT_LOGIN -> MessageConstant.USER_NOT_LOGIN;
            case CART_CHANGED -> MessageConstant.CART_CHANGED;
            case ADDRESS_CHANGED -> MessageConstant.ADDRESS_CHANGED;
            case PRICE_CHANGED -> MessageConstant.PRICE_CHANGED;
            case ITEM_CHANGED -> MessageConstant.ITEM_CHANGED;
            case ITEM_NOT_AVAILABLE -> MessageConstant.ITEM_NOT_AVAILABLE;
            case INVALID_CART_ITEM -> MessageConstant.INVALID_CART_ITEM;
            case SHOP_CLOSED -> MessageConstant.SHOP_CLOSED;
            case INVALID_SETTLEMENT -> MessageConstant.INVALID_SETTLEMENT;
            case SETTLEMENT_EXPIRED -> MessageConstant.SETTLEMENT_EXPIRED;
            case MAP_SERVICE_UNAVAILABLE -> MessageConstant.MAP_SERVICE_UNAVAILABLE;
            case DELIVERY_OUT_OF_RANGE -> MessageConstant.DISTANCE_MORE_THAN_5KM;
            default -> MessageConstant.UNKNOWN_ERROR;
        };
        return new BaseException(code, message);
    }
}
