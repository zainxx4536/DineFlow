package com.dineflow.exception;

/**
 * 上传文件失败异常
 */
public class UploadFileFailedException extends BaseException {

    public UploadFileFailedException() {
    }

    public UploadFileFailedException(String message) {
        super(message);
    }
}
