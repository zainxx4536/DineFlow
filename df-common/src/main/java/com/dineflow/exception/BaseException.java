package com.dineflow.exception;

/**
 * 基础业务异常,其他异常继承该类
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    // 错误信息依赖于抛出错误时传入的描述信息
    public BaseException(String msg) {
        super(msg);
    }

}
