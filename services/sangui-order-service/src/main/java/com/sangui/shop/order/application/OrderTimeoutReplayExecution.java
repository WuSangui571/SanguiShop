package com.sangui.shop.order.application;

import com.sangui.shop.order.domain.OrderRecord;

public record OrderTimeoutReplayExecution(
        OrderRecord order,
        String result,
        String errorCode,
        String reason
) {
}
