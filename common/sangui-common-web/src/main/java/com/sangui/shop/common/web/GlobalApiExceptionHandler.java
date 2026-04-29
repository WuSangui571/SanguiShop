package com.sangui.shop.common.web;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(SanguiException.class)
    public ResponseEntity<ApiResult<Void>> handleSanguiException(
            SanguiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.httpStatus())
                .body(ApiResult.failure(
                        exception.errorCode().code(),
                        exception.getMessage(),
                        traceId(request)
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResult.failure(
                        CommonErrorCode.VALIDATION_FAILED.code(),
                        CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                        traceId(request)
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.failure(
                        CommonErrorCode.INTERNAL_ERROR.code(),
                        CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                        traceId(request)
                ));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
