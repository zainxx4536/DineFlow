package com.dineflow.handler;

import com.dineflow.exception.BaseException;
import com.dineflow.exception.OrderSubmitUnknownException;
import com.dineflow.exception.UserNotLoginException;
import com.dineflow.result.Result;
import com.dineflow.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.regex.Pattern;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 只匹配员工用户名唯一约束。
     * 不提取或返回 Duplicate entry 中的原始值。
     */
    private static final Pattern USERNAME_UNIQUE_CONSTRAINT = Pattern.compile(
            "for key ['`](?:[^'`]+\\.)?idx_username['`]\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 下单结果未知。
     * 保留 code = 2 和原 requestId。
     */
    @ExceptionHandler(OrderSubmitUnknownException.class)
    public Result<Map<String, String>> orderSubmitUnknown(OrderSubmitUnknownException exception) {

        log.warn(
                "下单结果未确认，userId={}，requestId={}",
                ThreadLocalUtil.getCurrentId(),
                exception.getRequestId()
        );

        Result<Map<String, String>> result = new Result<>();

        result.setCode(2);
        result.setMsg("下单结果暂未确认，请使用原请求号重试确认");
        result.setData(Map.of("requestId", exception.getRequestId()));

        return result;
    }

    /**
     * 服务层明确抛出的未登录异常。
     */
    @ExceptionHandler(UserNotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<String> notLoggedIn() {

        return Result.error("用户未登录或登录已失效，请重新登录");
    }

    /**
     * 普通业务异常。
     * 消息必须是业务代码提供的公开文案，
     * 不能使用外部异常原文构造业务消息。
     */
    @ExceptionHandler(BaseException.class)
    public Result<String> businessException(BaseException exception) {

        log.warn(
                "业务请求被拒绝，currentId={}，exceptionType={}",
                ThreadLocalUtil.getCurrentId(),
                exception.getClass().getSimpleName()
        );

        String message = exception.getMessage();

        return Result.error(message == null || message.isBlank() ? "业务处理失败" : message);
    }

    /**
     * JSON 缺失、格式错误或字段类型无法解析。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> invalidJson() {

        return Result.error("请求格式错误，请检查JSON和字段类型");
    }

    /**
     * 参数绑定、Bean Validation、参数类型、
     * 必填参数或请求头、multipart 格式等错误。
     */
    @ExceptionHandler({
            BindException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class,
            MultipartException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> invalidParameters() {

        return Result.error("请求参数不合法，请检查后重试");
    }

    /**
     * 上传大小超限。
     * 比 MultipartException 更具体，优先匹配。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<String> uploadTooLarge() {

        return Result.error("上传文件或请求体超过允许大小");
    }

    /**
     * 数据完整性错误。
     * 只对员工用户名冲突做明确的公开映射。
     */
    @ExceptionHandler({
            DataIntegrityViolationException.class,
            SQLIntegrityConstraintViolationException.class
    })
    public ResponseEntity<Result<String>> dataIntegrity(Exception exception) {

        if (isUsernameUniqueConflict(exception)) {
            return ResponseEntity.ok(Result.error("用户名已存在"));
        }

        logFailure("数据完整性错误", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("数据处理失败，请稍后重试"));
    }

    /**
     * 其他数据库访问错误，例如数据库连接不可用。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> databaseFailure(DataAccessException exception) {

        logFailure("数据库访问失败", exception);

        return Result.error("系统暂时不可用，请稍后重试");
    }

    /**
     * 保留请求方式和内容类型对应的 HTTP 状态。
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Result<String>> invalidProtocol(Exception exception) {

        if (exception instanceof HttpRequestMethodNotSupportedException method) {

            HttpHeaders headers = new HttpHeaders();

            if (method.getSupportedHttpMethods() != null) {
                headers.setAllow(method.getSupportedHttpMethods());
            }

            return new ResponseEntity<>(
                    Result.error("请求方式不支持"),
                    headers,
                    HttpStatus.METHOD_NOT_ALLOWED
            );
        }

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error("请求内容类型不支持"));
    }

    /**
     * 其他未处理异常。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> unexpectedFailure(Exception exception) {

        logFailure("未预期的系统异常", exception);

        return Result.error("系统处理失败，请稍后重试");
    }

    /**
     * 遍历异常原因，识别明确的目标约束。
     * 这里只读取错误消息用于内部判断，不输出消息。
     */
    private boolean isUsernameUniqueConflict(Exception exception) {

        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {

            if (cause instanceof SQLException sql
                    && sql.getErrorCode() == 1062
                    && sql.getMessage() != null
                    && USERNAME_UNIQUE_CONSTRAINT.matcher(sql.getMessage()).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 只记录异常类别、当前身份、异常类型和代码位置。
     * 不将 Throwable 本身作为日志参数传入。
     */
    private void logFailure(String category, Exception exception) {

        StackTraceElement[] stack = exception.getStackTrace();

        String location = stack.length == 0 ? "unknown" : stack[0].toString();

        log.error(
                "处理失败，category={}，currentId={}，exceptionType={}，location={}",
                category,
                ThreadLocalUtil.getCurrentId(),
                exception.getClass().getName(),
                location
        );
    }
}