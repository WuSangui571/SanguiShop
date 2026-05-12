package com.sangui.shop.common.web;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.core.trace.TraceConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(SanguiException.class)
    public ResponseEntity<ApiResult<Void>> handleSanguiException(
            SanguiException exception,
            HttpServletRequest request
    ) {
        logOpsAuditIfNeeded(exception, request);
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
        log.error("Unhandled exception. uri={}, method={}, traceId={}",
                request.getRequestURI(),
                request.getMethod(),
                traceId(request),
                exception
        );

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

    private void logOpsAuditIfNeeded(SanguiException exception, HttpServletRequest request) {
        if (OpsAuditLogger.isLogged(request)) {
            return;
        }
        String action = actionFor(request.getRequestURI());
        if (action == null) {
            return;
        }
        boolean isOpsAuthPath = request.getRequestURI().startsWith("/api/users/ops/");
        if (!isOpsAuthPath && exception.httpStatus() != 403) {
            return;
        }
        OpsAuditLogger.log(request, OpsAuditLogger.event(request, action)
                .outcome(OpsAuditLogger.outcome(exception))
                .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                .errorCode(exception.errorCode().code())
                .reason(exception.getMessage())
                .build());
    }

    private String actionFor(String path) {
        if ("/api/users/ops/login".equals(path)) {
            return "ops.auth.login";
        }
        if ("/api/users/ops/session/refresh".equals(path)) {
            return "ops.auth.refresh";
        }
        if ("/internal/orders/compensation-records/query".equals(path)) {
            return "ops.order.compensation.query";
        }
        if ("/internal/orders/timeout-replays/manual".equals(path)) {
            return "ops.order.timeout-replay.manual";
        }
        if ("/internal/orders/timeout-replays/bulk".equals(path)) {
            return "ops.order.timeout-replay.bulk";
        }
        if ("/internal/payments/compensation-records/query".equals(path)) {
            return "ops.payment.compensation.query";
        }
        if ("/internal/payments/reconciliations/manual".equals(path)) {
            return "ops.payment.reconcile.manual";
        }
        if ("/internal/payments/reconciliations/bulk".equals(path)) {
            return "ops.payment.reconcile.bulk";
        }
        return null;
    }
}
