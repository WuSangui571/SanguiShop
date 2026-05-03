package com.sangui.shop.payment.application;

import com.sangui.shop.payment.domain.PaymentOrderRecord;

public record PaymentCompensationExecution(
        PaymentOrderRecord payment,
        String result,
        String errorCode,
        String reason
) {
}
