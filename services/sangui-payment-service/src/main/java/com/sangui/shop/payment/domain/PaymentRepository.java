package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo);

    List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit);

    Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo);

    Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status);

    Long createCallbackLog(PaymentCallbackLogDraft draft);

    void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status);

    void updateCallbackProcessStatus(Long callbackLogId, String processStatus);
}
