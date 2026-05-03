package com.sangui.shop.payment.api.dto;

public record BulkPaymentReconcileItemResponse(
        String result,
        String errorCode,
        String reason,
        PaymentCompensationRecordResponse payment
) {
}
