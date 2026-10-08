package com.dineflow.exception;

/**
 * 基础业务异常,其他异常继承该类
 */
public class BaseException extends RuntimeException {
    private final String errorCode;

    public String getErrorCode() { return errorCode; }

    public BaseException(com.dineflow.constant.BusinessErrorCode code, String message) {
        super(message);
        this.errorCode = code.name();
    }


    public BaseException() { this.errorCode = "BUSINESS_ERROR"; }

    // 错误信息依赖于抛出错误时传入的描述信息
    public BaseException(String msg) {
        super(msg);
        this.errorCode = "BUSINESS_ERROR";
    }

}
