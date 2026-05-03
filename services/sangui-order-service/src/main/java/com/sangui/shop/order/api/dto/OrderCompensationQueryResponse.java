package com.sangui.shop.order.api.dto;

import java.util.List;

public record OrderCompensationQueryResponse(
        Long shopId,
        Integer pageNo,
        Integer pageSize,
        Long total,
        List<OrderCompensationAggregateResponse> items
) {
}
