package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.api.dto.OrderCompensationRecordResponse;
import com.sangui.shop.order.application.OrderCompensationOpsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderCompensationController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalOrderCompensationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderCompensationOpsService orderCompensationOpsService;

    @Test
    void queryRecordsReturnsStableEnvelope() throws Exception {
        OrderCompensationRecordResponse order = new OrderCompensationRecordResponse(
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "cancelled",
                59900L,
                "trace-order",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                "cancelled",
                null,
                null,
                "trace-order-manual",
                "manual",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(orderCompensationOpsService.queryRecords(any()))
                .thenReturn(new OrderCompensationQueryResponse(1L, List.of(), List.of(order)));

        mockMvc.perform(post("/internal/orders/compensation-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "timeoutMinutes", 15,
                                "limit", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_COMPENSATION_RECORDS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-query"))
                .andExpect(jsonPath("$.data.cancelledOrders[0].orderNo").value("ORD-001"));
    }

    @Test
    void manualReplayReturnsStableEnvelope() throws Exception {
        OrderCompensationRecordResponse order = new OrderCompensationRecordResponse(
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "cancelled",
                59900L,
                "trace-order",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                "cancelled",
                null,
                null,
                "trace-order-manual",
                "manual",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(orderCompensationOpsService.manualReplay(any(), any()))
                .thenReturn(new ManualOrderTimeoutReplayResponse("cancelled", null, null, order));

        mockMvc.perform(post("/internal/orders/timeout-replays/manual")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "orderId", 101,
                                "timeoutMinutes", 15
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_TIMEOUT_REPLAYED_MANUALLY"))
                .andExpect(jsonPath("$.data.result").value("cancelled"))
                .andExpect(jsonPath("$.data.order.lastCompensationTrigger").value("manual"));
    }

    @Test
    void manualReplayValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/orders/timeout-replays/manual")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "orderId", 0,
                                "timeoutMinutes", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-validation"));
    }
}
