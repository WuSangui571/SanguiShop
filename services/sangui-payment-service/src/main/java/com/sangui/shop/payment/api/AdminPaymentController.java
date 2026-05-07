package com.sangui.shop.payment.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.application.PaymentPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentPayService paymentPayService;

    public AdminPaymentController(PaymentPayService paymentPayService) {
        this.paymentPayService = paymentPayService;
    }

    @GetMapping("/by-order/{orderId}")
    public ApiResult<PaymentResponse> getPaymentByOrderId(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        PaymentResponse response = paymentPayService.getAdminPaymentByOrderId(principal, orderId);
        return ApiResult.ok("ADMIN_PAYMENT_STATUS", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
