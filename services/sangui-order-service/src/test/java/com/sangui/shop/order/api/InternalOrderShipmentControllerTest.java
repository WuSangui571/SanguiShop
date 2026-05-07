package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.application.OrderShipmentService;
import com.sangui.shop.order.client.dto.FulfillmentOrderPageResponse;
import com.sangui.shop.order.client.dto.FulfillmentOrderResponse;
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

@WebMvcTest(InternalOrderShipmentController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalOrderShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderShipmentService orderShipmentService;

    @Test
    void queryFulfillmentRecordsReturnsEnvelope() throws Exception {
        when(orderShipmentService.queryFulfillmentRecords(any()))
                .thenReturn(new FulfillmentOrderPageResponse(1, 20, 1, List.of(response("paid", "unshipped"))));

        mockMvc.perform(post("/internal/orders/fulfillment-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-fulfillment-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "page", 1,
                                "size", 20,
                                "fulfillmentStatus", "unshipped"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_FULFILLMENT_RECORDS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-fulfillment-query"))
                .andExpect(jsonPath("$.data.items[0].fulfillmentStatus").value("unshipped"));
    }

    @Test
    void confirmShipmentReturnsShippedEnvelope() throws Exception {
        when(orderShipmentService.confirmShipment(any(), any()))
                .thenReturn(response("shipped", "shipped"));

        mockMvc.perform(post("/internal/orders/shipments/confirmations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "orderId", 101,
                                "requestId", "ship-001",
                                "carrier", "SF Express",
                                "trackingNo", "SF123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_SHIPPED"))
                .andExpect(jsonPath("$.data.status").value("shipped"))
                .andExpect(jsonPath("$.data.carrier").value("SF Express"));
    }

    private FulfillmentOrderResponse response(String status, String fulfillmentStatus) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-07T10:00:00+08:00");
        return new FulfillmentOrderResponse(
                101L,
                "ORD-101",
                1L,
                "10001",
                status,
                fulfillmentStatus,
                59900L,
                "SF Express",
                "SF123",
                timestamp,
                "trace-order",
                timestamp,
                timestamp
        );
    }
}
