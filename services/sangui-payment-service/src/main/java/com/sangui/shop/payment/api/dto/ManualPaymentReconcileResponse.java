package com.sangui.shop.payment.api.dto;

public record ManualPaymentReconcileResponse(
        String result,
        String errorCode,
        String reason,
        PaymentCompensationRecordResponse payment
) {
}
