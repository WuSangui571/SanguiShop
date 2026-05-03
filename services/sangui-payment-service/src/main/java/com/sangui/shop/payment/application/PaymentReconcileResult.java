package com.sangui.shop.payment.application;

public record PaymentReconcileResult(
        Long shopId,
        int scannedCount,
        int settledCount,
        int skippedCount,
        int failedCount
) {
}
