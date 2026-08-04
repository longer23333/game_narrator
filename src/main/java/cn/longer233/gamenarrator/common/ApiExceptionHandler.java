package cn.longer233.gamenarrator.common;

import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(TaskNotFoundException exception) {
        log.warn("API_ERROR code=TASK_NOT_FOUND message={}", exception.getMessage());
        return error("TASK_NOT_FOUND", exception.getMessage(), "确认任务 ID 是否正确");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError staticResourceNotFound(NoResourceFoundException exception) {
        log.debug("STATIC_RESOURCE_NOT_FOUND path={}", exception.getResourcePath());
        return error("RESOURCE_NOT_FOUND", "请求的资源不存在", "检查资源地址");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError missingParameter(MissingServletRequestParameterException exception) {
        String message = "缺少必填字段：" + exception.getParameterName();
        log.warn("API_ERROR code=MISSING_PARAMETER parameter={}", exception.getParameterName());
        return error("MISSING_PARAMETER", message, "检查表单字段名称和提交内容");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidBody(MethodArgumentNotValidException exception) {
        String field = exception.getBindingResult().getFieldErrors().isEmpty() ? "请求内容"
                : exception.getBindingResult().getFieldErrors().getFirst().getField();
        String message = "字段无效：" + field;
        log.warn("API_ERROR code=INVALID_REQUEST field={}", field);
        return error("INVALID_REQUEST", message, "重新解析后选择一个可用格式再试");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidType(MethodArgumentTypeMismatchException exception) {
        String message = "字段格式错误：" + exception.getName();
        log.warn("API_ERROR code=INVALID_FIELD_TYPE field={} value={}",
                exception.getName(), exception.getValue());
        return error("INVALID_FIELD_TYPE", message, "检查枚举值或数字格式");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiError unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        log.warn("API_ERROR code=UNSUPPORTED_MEDIA_TYPE contentType={}", exception.getContentType());
        return error(
                "UNSUPPORTED_MEDIA_TYPE",
                "请求格式不受支持：" + exception.getContentType(),
                "上传接口必须使用 multipart/form-data"
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiError uploadTooLarge(MaxUploadSizeExceededException exception) {
        log.warn("API_ERROR code=VIDEO_TOO_LARGE", exception);
        return error("VIDEO_TOO_LARGE", "视频超过服务器允许的上传大小", "压缩视频或调整 multipart 配置");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException exception) {
        log.warn("API_ERROR code=INVALID_REQUEST message={}", exception.getMessage());
        return error("INVALID_REQUEST", exception.getMessage(), "检查文件格式和表单参数");
    }

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError externalServiceUnavailable(ResourceAccessException exception) {
        log.warn("API_ERROR code=EXTERNAL_SERVICE_UNAVAILABLE message={}", exception.getMessage());
        return error("EXTERNAL_SERVICE_UNAVAILABLE",
                "无法连接外部素材服务，请检查网络或代理后重试",
                "在线素材源不可用时，本地任务和已导入素材仍可继续使用");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError operationUnavailable(IllegalStateException exception) {
        log.warn("API_ERROR code=OPERATION_UNAVAILABLE message={}", exception.getMessage());
        return error("OPERATION_UNAVAILABLE", exception.getMessage(),
                "检查依赖工具、内容授权和当前是否已有下载任务");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrentModification(OptimisticLockingFailureException exception) {
        log.warn("API_ERROR code=CONCURRENT_MODIFICATION");
        return error("CONCURRENT_MODIFICATION", "任务已被后台流程或另一个编辑操作更新",
                "刷新任务后重新提交本次修改");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError unexpected(Exception exception) {
        log.error("API_ERROR code=INTERNAL_ERROR type={} message={}",
                exception.getClass().getName(), exception.getMessage(), exception);
        return error(
                "INTERNAL_ERROR",
                "服务器处理失败，请根据 traceId 查看控制台或日志文件",
                "查看 logs/game-narrator.log 中相同 traceId 的异常堆栈"
        );
    }

    private ApiError error(String code, String message, String suggestion) {
        return new ApiError(
                code,
                message,
                suggestion,
                MDC.get(RequestTraceFilter.TRACE_ID),
                Instant.now().toString()
        );
    }

    public record ApiError(
            String code,
            String message,
            String suggestion,
            String traceId,
            String timestamp
    ) {
    }
}
