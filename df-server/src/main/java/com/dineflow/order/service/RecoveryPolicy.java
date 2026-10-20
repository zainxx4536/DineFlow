package com.dineflow.order.service;

import com.wechat.pay.java.core.exception.ServiceException;

import java.time.LocalDateTime;

public final class RecoveryPolicy {

    public static final int MAX_QUERIES = 20;

    public static final int MAX_REFUND_ATTEMPTS = 3;

    private RecoveryPolicy() {
    }

    public static LocalDateTime nextTime(LocalDateTime now, int count) {
        int minutes = count <= 1 ? 1 : count == 2 ? 3 : count == 3 ? 10 : 30;
        return now.plusMinutes(minutes);
    }

    public static String error(Exception exception) {
        // 不记录异常原文，避免包含Token、请求体或通道敏感信息。
        String result = exception.getClass().getSimpleName();
        if (exception instanceof ServiceException service
                && service.getErrorCode() != null
                && service.getErrorCode().matches("[A-Z0-9_]{1,64}")) {
            result += ":" + service.getErrorCode();
        }
        return result.substring(0, Math.min(200, result.length()));
    }
}
