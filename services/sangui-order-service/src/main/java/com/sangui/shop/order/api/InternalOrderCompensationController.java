package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.OpsAuditLogger;
import com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayRequest;
import com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayRequest;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.OrderCompensationQueryRequest;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.application.OrderCompensationOpsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderCompensationController {

    private final OrderCompensationOpsService orderCompensationOpsService;

    public InternalOrderCompensationController(OrderCompensationOpsService orderCompensationOpsService) {
        this.orderCompensationOpsService = orderCompensationOpsService;
    }

    @PostMapping("/compensation-records/query")
    public ApiResult<OrderCompensationQueryResponse> queryRecords(
            @Valid @RequestBody OrderCompensationQueryRequest request,
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        OrderCompensationQueryResponse response = orderCompensationOpsService.queryRecords(principal, request);
        return ApiResult.ok("ORDER_COMPENSATION_RECORDS_FETCHED", response, traceId);
    }

    @PostMapping("/timeout-replays/manual")
    public ApiResult<ManualOrderTimeoutReplayResponse> manualReplay(
            @Valid @RequestBody ManualOrderTimeoutReplayRequest request,
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        try {
            ManualOrderTimeoutReplayResponse response = orderCompensationOpsService.manualReplay(principal, request, traceId);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.order.timeout-replay.manual")
                    .outcome("success")
                    .result(response.result())
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("order")
                    .targetId(String.valueOf(request.orderId()))
                    .build());
            return ApiResult.ok("ORDER_TIMEOUT_REPLAYED_MANUALLY", response, traceId);
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.order.timeout-replay.manual")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("order")
                    .targetId(String.valueOf(request.orderId()))
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }

    @PostMapping("/timeout-replays/bulk")
    public ApiResult<BulkOrderTimeoutReplayResponse> bulkReplay(
            @Valid @RequestBody BulkOrderTimeoutReplayRequest request,
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String traceId = OpsAuditLogger.traceId(httpRequest);
        try {
            BulkOrderTimeoutReplayResponse response = orderCompensationOpsService.bulkReplay(principal, request, traceId);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.order.timeout-replay.bulk")
                    .outcome("success")
                    .result(Boolean.TRUE.equals(request.dryRun()) ? "dry-run" : "completed")
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("order")
                    .targetCount(response.matchedCount())
                    .dryRun(request.dryRun())
                    .build());
            return ApiResult.ok("ORDER_TIMEOUT_REPLAYED_IN_BULK", response, traceId);
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.order.timeout-replay.bulk")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .operator(request.operator())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .targetType("order")
                    .targetCount(request.orderIds() == null ? null : request.orderIds().size())
                    .dryRun(request.dryRun())
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }
}
