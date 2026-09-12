package com.dineflow.exception;

/**
 * 上传文件为空异常
 */
public class UploadFileIsNullException extends BaseException {

    public UploadFileIsNullException() {
    }

    public UploadFileIsNullException(String message) {
        super(message);
    }
}
