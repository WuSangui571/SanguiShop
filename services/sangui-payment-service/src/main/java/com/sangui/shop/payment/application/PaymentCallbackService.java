package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.api.dto.PaymentCallbackRequest;
import com.sangui.shop.payment.api.dto.PaymentCallbackResponse;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.domain.PaymentCallbackLogDraft;
import com.sangui.shop.payment.domain.PaymentCallbackLogRecord;
import com.sangui.shop.payment.domain.PaymentErrorCode;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PaymentCallbackService {

    private static final String PROCESS_STATUS_PROCESSED = "processed";
    private static final String PROCESS_STATUS_FAILED = "failed";
    private static final String PROCESS_STATUS_IGNORED = "ignored";

    private final PaymentRepository paymentRepository;
    private final PaymentPayService paymentPayService;

    public PaymentCallbackService(PaymentRepository paymentRepository, PaymentPayService paymentPayService) {
        this.paymentRepository = paymentRepository;
        this.paymentPayService = paymentPayService;
    }

    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request, String traceId) {
        PaymentCallbackLogRecord callbackLog = recordCallback(request, traceId);
        try {
            PaymentOrderRecord payment = paymentRepository.findByPaymentNo(request.shopId(), normalizeRequired(request.paymentNo()))
                    .orElseThrow(() -> new SanguiException(PaymentErrorCode.PAYMENT_NOT_FOUND, 404));
            ensureChannelMatches(payment, request);

            CallbackTradeStatus tradeStatus = CallbackTradeStatus.fromValue(request.tradeStatus());
            if (tradeStatus == CallbackTradeStatus.SUCCESS) {
                return handleSuccess(request, payment, callbackLog, traceId);
            }
            return handleTerminalFailure(payment, callbackLog);
        } catch (SanguiException exception) {
            paymentRepository.updateCallbackProcessStatus(callbackLog.id(), PROCESS_STATUS_FAILED);
            throw exception;
        }
    }

    private PaymentCallbackResponse handleSuccess(
            PaymentCallbackRequest request,
            PaymentOrderRecord payment,
            PaymentCallbackLogRecord callbackLog,
            String traceId
    ) {
        if (!Objects.equals(payment.amountCent(), request.paidAmountCent())) {
            paymentRepository.updateCallbackProcessStatus(callbackLog.id(), PROCESS_STATUS_FAILED);
            throw new SanguiException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH, 409);
        }
        PaymentResponse settled = paymentPayService.settlePayment(payment, traceId);
        paymentRepository.updateCallbackProcessStatus(callbackLog.id(), PROCESS_STATUS_PROCESSED);
        return new PaymentCallbackResponse(
                settled.paymentNo(),
                settled.channel(),
                request.channelTradeNo().trim(),
                settled.status(),
                PROCESS_STATUS_PROCESSED
        );
    }

    private PaymentCallbackResponse handleTerminalFailure(PaymentOrderRecord payment, PaymentCallbackLogRecord callbackLog) {
        if (payment.status() == PaymentStatus.PAID) {
            paymentRepository.updateCallbackProcessStatus(callbackLog.id(), PROCESS_STATUS_IGNORED);
            return new PaymentCallbackResponse(
                    payment.paymentNo(),
                    payment.channel(),
                    callbackLog.channelTradeNo(),
                    payment.status().value(),
                    PROCESS_STATUS_IGNORED
            );
        }

        paymentRepository.updatePaymentStatus(payment.shopId(), payment.id(), PaymentStatus.FAILED);
        paymentRepository.updateCallbackProcessStatus(callbackLog.id(), PROCESS_STATUS_PROCESSED);
        return new PaymentCallbackResponse(
                payment.paymentNo(),
                payment.channel(),
                callbackLog.channelTradeNo(),
                PaymentStatus.FAILED.value(),
                PROCESS_STATUS_PROCESSED
        );
    }

    private PaymentCallbackLogRecord recordCallback(PaymentCallbackRequest request, String traceId) {
        String channel = normalizeRequired(request.channel());
        String channelTradeNo = normalizeRequired(request.channelTradeNo());
        PaymentCallbackLogDraft draft = new PaymentCallbackLogDraft(
                request.shopId(),
                normalizeRequired(request.paymentNo()),
                channel,
                channelTradeNo,
                normalizeOptional(request.callbackType()) == null ? "payment" : normalizeOptional(request.callbackType()),
                toPayloadJson(request),
                normalizeOptional(traceId)
        );
        try {
            Long callbackLogId = paymentRepository.createCallbackLog(draft);
            return new PaymentCallbackLogRecord(
                    callbackLogId,
                    draft.shopId(),
                    draft.paymentNo(),
                    draft.channel(),
                    draft.channelTradeNo(),
                    draft.callbackType(),
                    "received",
                    draft.traceId()
            );
        } catch (DuplicateKeyException exception) {
            return paymentRepository.findCallbackLog(channel, channelTradeNo)
                    .orElseThrow(() -> exception);
        }
    }

    private void ensureChannelMatches(PaymentOrderRecord payment, PaymentCallbackRequest request) {
        if (!payment.channel().equalsIgnoreCase(normalizeRequired(request.channel()))) {
            throw new SanguiException(PaymentErrorCode.PAYMENT_CALLBACK_CHANNEL_MISMATCH, 409);
        }
    }

    private String toPayloadJson(PaymentCallbackRequest request) {
        return "{"
                + "\"paymentNo\":" + quote(request.paymentNo()) + ","
                + "\"channel\":" + quote(request.channel()) + ","
                + "\"channelTradeNo\":" + quote(request.channelTradeNo()) + ","
                + "\"tradeStatus\":" + quote(request.tradeStatus()) + ","
                + "\"paidAmountCent\":" + request.paidAmountCent() + ","
                + "\"eventTime\":" + quote(request.eventTime()) + ","
                + "\"rawPayload\":" + quote(request.rawPayload())
                + "}";
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

    private enum CallbackTradeStatus {
        SUCCESS,
        FAILED;

        private static CallbackTradeStatus fromValue(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "SUCCESS", "PAID", "TRADE_SUCCESS" -> SUCCESS;
                case "FAILED", "FAIL", "CLOSED", "TRADE_CLOSED" -> FAILED;
                default -> throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
            };
        }
    }
}
