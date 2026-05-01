package com.sangui.shop.order.client.dto;

public record CancelExpiredOrdersResponse(
        Long shopId,
        int scannedCount,
        int cancelledCount,
        int skippedCount
) {
}
