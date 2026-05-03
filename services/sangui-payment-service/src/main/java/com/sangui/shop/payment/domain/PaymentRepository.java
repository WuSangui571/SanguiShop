package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo);

    List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit);

    List<PaymentOrderRecord> findFailedPayments(Long shopId, int limit);

    Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo);

    Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status);

    Long createCallbackLog(PaymentCallbackLogDraft draft);

    void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status);

    void updateCompensationMetadata(
            Long shopId,
            Long paymentId,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator,
            LocalDateTime compensatedAt
    );

    void appendCompensationAttempt(
            Long shopId,
            Long paymentId,
            Long orderId,
            String paymentNo,
            String orderNo,
            String reservationNo,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator
    );

    void updateCallbackProcessStatus(Long callbackLogId, String processStatus);
}
