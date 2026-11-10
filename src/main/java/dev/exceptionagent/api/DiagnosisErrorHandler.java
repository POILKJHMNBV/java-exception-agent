package dev.exceptionagent.api;

import dev.exceptionagent.diagnosis.DiagnosisFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 所有诊断失败统一映射，响应不序列化异常或底层 cause。 */
@RestControllerAdvice
public final class DiagnosisErrorHandler {
    /** 将固定失败码转换为产品约定的 HTTP 状态。 */
    @ExceptionHandler(DiagnosisFailure.class)
    public ResponseEntity<ApiError> diagnosis(DiagnosisFailure failure, HttpServletRequest request) {
        return error(request, failure.code());
    }

    /** 缺少请求体及读取失败均为无效输入。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpServletRequest request) { return error(request, "INVALID_REQUEST"); }

    /** 内容类型错误不能进入模型。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(HttpServletRequest request) { return error(request, "UNSUPPORTED_MEDIA_TYPE"); }

    /** 未预期的内部异常也只返回固定文字，避免泄露堆栈和输入。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(HttpServletRequest request) { return error(request, "INTERNAL_ERROR"); }

    /** 与过滤器共享关联标识和错误格式。 */
    private static ResponseEntity<ApiError> error(HttpServletRequest request, String code) {
        return ResponseEntity.status(ApiError.status(code)).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of((String) request.getAttribute(DiagnosisRequestFilter.ID_ATTRIBUTE), code));
    }
}
