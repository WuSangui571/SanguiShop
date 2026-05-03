package com.sangui.shop.payment.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryRequest;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.application.PaymentCompensationOpsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentCompensationController {

    private final PaymentCompensationOpsService paymentCompensationOpsService;

    public InternalPaymentCompensationController(PaymentCompensationOpsService paymentCompensationOpsService) {
        this.paymentCompensationOpsService = paymentCompensationOpsService;
    }

    @PostMapping("/compensation-records/query")
    public ApiResult<PaymentCompensationQueryResponse> queryRecords(
            @Valid @RequestBody PaymentCompensationQueryRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        PaymentCompensationQueryResponse response = paymentCompensationOpsService.queryRecords(request);
        return ApiResult.ok("PAYMENT_COMPENSATION_RECORDS_FETCHED", response, traceId);
    }

    @PostMapping("/reconciliations/manual")
    public ApiResult<ManualPaymentReconcileResponse> manualReconcile(
            @Valid @RequestBody ManualPaymentReconcileRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        ManualPaymentReconcileResponse response = paymentCompensationOpsService.manualReconcile(request, traceId);
        return ApiResult.ok("PAYMENT_RECONCILED_MANUALLY", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
