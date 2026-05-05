package com.sangui.shop.payment.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.OpsAuditLogger;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileResponse;
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
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        PaymentCompensationQueryResponse response = paymentCompensationOpsService.queryRecords(principal, request);
        return ApiResult.ok("PAYMENT_COMPENSATION_RECORDS_FETCHED", response, traceId);
    }

    @PostMapping("/reconciliations/manual")
    public ApiResult<ManualPaymentReconcileResponse> manualReconcile(
            @Valid @RequestBody ManualPaymentReconcileRequest request,
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        try {
            ManualPaymentReconcileResponse response = paymentCompensationOpsService.manualReconcile(principal, request, traceId);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.payment.reconcile.manual")
                    .outcome("success")
                    .result(response.result())
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("payment")
                    .targetId(request.paymentNo())
                    .build());
            return ApiResult.ok("PAYMENT_RECONCILED_MANUALLY", response, traceId);
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.payment.reconcile.manual")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("payment")
                    .targetId(request.paymentNo())
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }

    @PostMapping("/reconciliations/bulk")
    public ApiResult<BulkPaymentReconcileResponse> bulkReconcile(
            @Valid @RequestBody BulkPaymentReconcileRequest request,
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        try {
            BulkPaymentReconcileResponse response = paymentCompensationOpsService.bulkReconcile(principal, request, traceId);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.payment.reconcile.bulk")
                    .outcome("success")
                    .result(Boolean.TRUE.equals(request.dryRun()) ? "dry-run" : "completed")
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("payment")
                    .targetCount(response.matchedCount())
                    .dryRun(request.dryRun())
                    .build());
            return ApiResult.ok("PAYMENT_RECONCILED_IN_BULK", response, traceId);
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.payment.reconcile.bulk")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("payment")
                    .targetCount(request.paymentNos() == null ? null : request.paymentNos().size())
                    .dryRun(request.dryRun())
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }
}
