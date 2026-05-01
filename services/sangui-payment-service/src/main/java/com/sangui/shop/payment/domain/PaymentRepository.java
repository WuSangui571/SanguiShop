package com.sangui.shop.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo);

    Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status);

    void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status);
}
