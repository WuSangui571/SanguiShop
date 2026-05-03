package com.sangui.shop.payment.api.dto;

import java.util.List;

public record BulkPaymentReconcileResponse(
        Long shopId,
        boolean dryRun,
        int matchedCount,
        int executedCount,
        int successCount,
        int skippedCount,
        int failedCount,
        List<BulkPaymentReconcileItemResponse> items
) {
}
