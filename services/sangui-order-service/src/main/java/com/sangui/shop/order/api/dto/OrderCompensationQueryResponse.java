package com.sangui.shop.order.api.dto;

import java.util.List;

public record OrderCompensationQueryResponse(
        Long shopId,
        List<OrderCompensationRecordResponse> timeoutOrders,
        List<OrderCompensationRecordResponse> cancelledOrders
) {
}
