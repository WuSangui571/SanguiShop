package com.sangui.shop.order.api.dto;

public record ManualOrderTimeoutReplayResponse(
        String result,
        String errorCode,
        String reason,
        OrderCompensationRecordResponse order
) {
}
