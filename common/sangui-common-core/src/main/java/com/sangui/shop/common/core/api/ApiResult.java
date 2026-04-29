package com.sangui.shop.common.core.api;

import java.time.OffsetDateTime;

public record ApiResult<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {

    public static <T> ApiResult<T> ok(String code, T data, String traceId) {
        return new ApiResult<>(code, "ok", data, traceId, OffsetDateTime.now());
    }

    public static <T> ApiResult<T> failure(String code, String message, String traceId) {
        return new ApiResult<>(code, message, null, traceId, OffsetDateTime.now());
    }
}
