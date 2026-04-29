package com.sangui.shop.common.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CommonErrorCodeTest {

    @Test
    void containsBaselineErrorCodes() {
        Set<String> codes = Arrays.stream(CommonErrorCode.values())
                .map(CommonErrorCode::code)
                .collect(Collectors.toSet());

        assertThat(codes).contains(
                "OK",
                "VALIDATION_FAILED",
                "AUTH_TOKEN_MISSING",
                "AUTH_TOKEN_EXPIRED",
                "AUTH_FORBIDDEN",
                "RATE_LIMITED",
                "CONFIG_SECRET_MISSING",
                "DOWNSTREAM_TIMEOUT",
                "IDEMPOTENCY_CONFLICT",
                "INTERNAL_ERROR"
        );
    }

    @Test
    void exposesNonBlankDefaultMessages() {
        assertThat(CommonErrorCode.values())
                .allSatisfy(errorCode -> assertThat(errorCode.defaultMessage()).isNotBlank());
    }
}
