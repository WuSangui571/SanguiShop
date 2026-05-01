package com.sangui.shop.payment.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.payment.api.dto.CreatePaymentRequest;
import com.sangui.shop.payment.api.dto.PaymentCallbackRequest;
import com.sangui.shop.payment.api.dto.PaymentCallbackResponse;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.application.PaymentCallbackService;
import com.sangui.shop.payment.application.PaymentPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentPayService paymentPayService;
    private final PaymentCallbackService paymentCallbackService;

    public PaymentController(PaymentPayService paymentPayService, PaymentCallbackService paymentCallbackService) {
        this.paymentPayService = paymentPayService;
        this.paymentCallbackService = paymentCallbackService;
    }

    @PostMapping
    public ApiResult<PaymentResponse> createPayment(
            SanguiPrincipal principal,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        PaymentResponse response = paymentPayService.pay(principal, request, traceId);
        return ApiResult.ok("PAYMENT_PAID", response, traceId);
    }

    @GetMapping("/{paymentNo}")
    public ApiResult<PaymentResponse> getPayment(
            SanguiPrincipal principal,
            @PathVariable String paymentNo,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        PaymentResponse response = paymentPayService.getPayment(principal, paymentNo);
        return ApiResult.ok("PAYMENT_STATUS", response, traceId);
    }

    @PostMapping("/callbacks/mock")
    public ApiResult<PaymentCallbackResponse> handleMockCallback(
            @Valid @RequestBody PaymentCallbackRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        PaymentCallbackResponse response = paymentCallbackService.handleCallback(request, traceId);
        return ApiResult.ok("PAYMENT_CALLBACK_PROCESSED", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
