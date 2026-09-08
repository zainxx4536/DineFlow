package com.dineflow.exception;

/**
 * 删除不允许异常
 */
public class DeletionNotAllowedException extends BaseException {

    public DeletionNotAllowedException(String msg) {
        super(msg);
    }

}
