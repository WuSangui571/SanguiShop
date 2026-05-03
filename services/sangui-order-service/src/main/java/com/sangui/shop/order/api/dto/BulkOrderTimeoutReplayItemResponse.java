package com.sangui.shop.order.api.dto;

public record BulkOrderTimeoutReplayItemResponse(
        String result,
        String errorCode,
        String reason,
        OrderCompensationRecordResponse order
) {
}
