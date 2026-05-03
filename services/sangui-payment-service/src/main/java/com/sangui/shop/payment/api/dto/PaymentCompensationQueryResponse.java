package com.sangui.shop.payment.api.dto;

import java.util.List;

public record PaymentCompensationQueryResponse(
        Long shopId,
        Integer pageNo,
        Integer pageSize,
        Long total,
        List<PaymentCompensationAggregateResponse> items
) {
}
