package com.sangui.shop.order.api.dto;

import java.util.List;

public record BulkOrderTimeoutReplayResponse(
        Long shopId,
        boolean dryRun,
        int matchedCount,
        int executedCount,
        int successCount,
        int skippedCount,
        int failedCount,
        List<BulkOrderTimeoutReplayItemResponse> items
) {
}
