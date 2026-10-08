package com.dineflow.handler;

import com.dineflow.constant.MessageConstant;
import com.dineflow.exception.BaseException;
import com.dineflow.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     */
    @ExceptionHandler(BaseException.class)
    public Result<String> exceptionHandler(BaseException e) {
        log.error("异常信息：{}", e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 捕获新增员工时用户名相同的异常
     */
    @ExceptionHandler
    public Result<String> exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        //错误信息示例：Duplicate entry 'name' for key 'employee.idx_username'
        String message = ex.getMessage();
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String username = split[2];
            String msg = username + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        } else {
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
    // 死锁/锁等待作为明确业务冲突返回，客户端刷新后重新确认，不自动重放写请求。
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public Result<String> concurrentWrite(RuntimeException e) {
        return Result.error("CART_CHANGED", MessageConstant.CART_CHANGED);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<String> malformedInput(RuntimeException e) {
        return Result.error("INVALID_PARAMETER", MessageConstant.INVALID_PARAMETER);
    }
}
