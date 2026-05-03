package com.sangui.shop.payment.api.dto;

import java.util.List;

public record PaymentCompensationQueryResponse(
        Long shopId,
        List<PaymentCompensationRecordResponse> createdPayments,
        List<PaymentCompensationRecordResponse> failedPayments
) {
}
