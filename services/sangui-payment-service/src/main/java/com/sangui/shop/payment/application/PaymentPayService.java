package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.payment.api.dto.CreatePaymentRequest;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.client.OrderPaymentClient;
import com.sangui.shop.payment.client.OrderPaymentSnapshot;
import com.sangui.shop.payment.client.ProductInventoryClient;
import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentPayService {

    private final PaymentRepository paymentRepository;
    private final OrderPaymentClient orderPaymentClient;
    private final ProductInventoryClient productInventoryClient;

    public PaymentPayService(
            PaymentRepository paymentRepository,
            OrderPaymentClient orderPaymentClient,
            ProductInventoryClient productInventoryClient
    ) {
        this.paymentRepository = paymentRepository;
        this.orderPaymentClient = orderPaymentClient;
        this.productInventoryClient = productInventoryClient;
    }

    public PaymentResponse pay(SanguiPrincipal principal, CreatePaymentRequest request, String traceId) {
        String paymentNo = normalizeRequired(request.paymentNo());
        String channel = normalizeRequired(request.channel());

        PaymentOrderRecord existing = paymentRepository.findByPaymentNo(principal.shopId(), paymentNo).orElse(null);
        if (existing != null) {
            return replayExistingPayment(existing, principal, request.orderId(), channel, traceId);
        }

        OrderPaymentSnapshot order = orderPaymentClient.getPayableOrder(principal.shopId(), principal.userId(), request.orderId());
        PaymentCreateDraft draft = new PaymentCreateDraft(
                principal.shopId(),
                order.orderId(),
                order.orderNo(),
                principal.userId(),
                order.reservationNo(),
                paymentNo,
                channel,
                order.totalAmountCent(),
                normalizeOptional(traceId)
        );

        Long paymentId;
        try {
            paymentId = createPaymentOrder(draft);
        } catch (DuplicateKeyException exception) {
            PaymentOrderRecord duplicated = paymentRepository.findByPaymentNo(principal.shopId(), paymentNo)
                    .orElseThrow(() -> exception);
            return replayExistingPayment(duplicated, principal, request.orderId(), channel, traceId);
        }

        return completePayment(toCreatedRecord(paymentId, draft));
    }

    public PaymentResponse getPayment(SanguiPrincipal principal, String paymentNo) {
        PaymentOrderRecord payment = paymentRepository.findByPaymentNo(principal.shopId(), normalizeRequired(paymentNo))
                .orElseThrow(() -> new SanguiException(com.sangui.shop.payment.domain.PaymentErrorCode.PAYMENT_NOT_FOUND, 404));
        if (!Objects.equals(payment.userId(), principal.userId())) {
            throw new SanguiException(com.sangui.shop.payment.domain.PaymentErrorCode.PAYMENT_NOT_FOUND, 404);
        }
        return toResponse(payment);
    }

    public PaymentResponse settlePayment(PaymentOrderRecord payment, String traceId) {
        if (payment.status() == PaymentStatus.PAID) {
            return toResponse(payment);
        }
        if (payment.status() != PaymentStatus.CREATED) {
            throw new SanguiException(com.sangui.shop.payment.domain.PaymentErrorCode.PAYMENT_STATUS_INVALID, 409);
        }
        return completePayment(payment.withStatus(PaymentStatus.CREATED), traceId);
    }

    private PaymentResponse replayExistingPayment(
            PaymentOrderRecord existing,
            SanguiPrincipal principal,
            Long orderId,
            String channel,
            String traceId
    ) {
        if (!Objects.equals(existing.shopId(), principal.shopId())
                || !Objects.equals(existing.userId(), principal.userId())
                || !Objects.equals(existing.orderId(), orderId)
                || !Objects.equals(existing.channel(), channel)) {
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }
        if (existing.status() == PaymentStatus.PAID) {
            return toResponse(existing);
        }
        return settlePayment(existing, traceId);
    }

    private PaymentResponse completePayment(PaymentOrderRecord payment) {
        return completePayment(payment, payment.traceId());
    }

    private PaymentResponse completePayment(PaymentOrderRecord payment, String traceId) {
        orderPaymentClient.confirmPaid(
                payment.shopId(),
                payment.userId(),
                payment.orderId(),
                payment.paymentNo(),
                payment.amountCent(),
                normalizeOptional(traceId)
        );
        productInventoryClient.confirmReservation(
                payment.shopId(),
                payment.reservationNo(),
                normalizeOptional(traceId)
        );
        updatePaymentStatus(payment.shopId(), payment.id(), PaymentStatus.PAID);
        return toResponse(payment.withStatus(PaymentStatus.PAID));
    }

    @Transactional
    protected Long createPaymentOrder(PaymentCreateDraft draft) {
        return paymentRepository.createPaymentOrder(draft, PaymentStatus.CREATED);
    }

    @Transactional
    protected void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status) {
        paymentRepository.updatePaymentStatus(shopId, paymentId, status);
    }

    private PaymentOrderRecord toCreatedRecord(Long paymentId, PaymentCreateDraft draft) {
        return new PaymentOrderRecord(
                paymentId,
                draft.shopId(),
                draft.orderId(),
                draft.orderNo(),
                draft.userId(),
                draft.reservationNo(),
                draft.paymentNo(),
                draft.channel(),
                draft.amountCent(),
                PaymentStatus.CREATED,
                draft.traceId(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private PaymentResponse toResponse(PaymentOrderRecord payment) {
        return new PaymentResponse(
                payment.id(),
                payment.paymentNo(),
                payment.orderId(),
                payment.orderNo(),
                payment.shopId(),
                payment.userId(),
                payment.channel(),
                payment.status().value(),
                payment.amountCent()
        );
    }

    private String normalizeRequired(String value) {
        String trimmed = normalizeOptional(value);
        if (trimmed == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
