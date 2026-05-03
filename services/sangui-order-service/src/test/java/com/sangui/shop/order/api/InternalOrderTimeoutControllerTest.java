package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.application.OrderTimeoutCancelService;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderTimeoutController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalOrderTimeoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderTimeoutCancelService orderTimeoutCancelService;

    @Test
    void cancelExpiredOrdersReturnsStableEnvelope() throws Exception {
        when(orderTimeoutCancelService.cancelExpiredOrders(any(), any()))
                .thenReturn(new CancelExpiredOrdersResponse(1L, 2, 1, 1, 0));

        mockMvc.perform(post("/internal/orders/timeout-cancellations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "timeoutMinutes", 15,
                                "limit", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_TIMEOUT_CANCELLED"))
                .andExpect(jsonPath("$.traceId").value("trace-timeout"))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }

    @Test
    void cancelExpiredOrdersValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/orders/timeout-cancellations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-timeout-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "timeoutMinutes", 0,
                                "limit", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-timeout-validation"));
    }
}
