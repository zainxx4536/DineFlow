package com.dineflow.exception;

import lombok.Getter;

@Getter
public class OrderSubmitUnknownException extends BaseException {

    private final String requestId;

    public OrderSubmitUnknownException(String requestId) {
        super("下单结果暂未确认，请使用原请求号重试确认");
        this.requestId = requestId;
    }

}