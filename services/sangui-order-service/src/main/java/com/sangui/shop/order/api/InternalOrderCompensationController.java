package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
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
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderCompensationQueryResponse response = orderCompensationOpsService.queryRecords(request);
        return ApiResult.ok("ORDER_COMPENSATION_RECORDS_FETCHED", response, traceId);
    }

    @PostMapping("/timeout-replays/manual")
    public ApiResult<ManualOrderTimeoutReplayResponse> manualReplay(
            @Valid @RequestBody ManualOrderTimeoutReplayRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        ManualOrderTimeoutReplayResponse response = orderCompensationOpsService.manualReplay(request, traceId);
        return ApiResult.ok("ORDER_TIMEOUT_REPLAYED_MANUALLY", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
