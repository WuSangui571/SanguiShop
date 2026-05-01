package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.application.OrderPaymentService;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderPaymentController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalOrderPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderPaymentService orderPaymentService;

    @Test
    void getPayableOrderReturnsSnapshotEnvelope() throws Exception {
        when(orderPaymentService.getPayableOrder(any()))
                .thenReturn(new OrderPaymentSnapshotResponse(101L, "ORD-001", 1L, "10001", "ord:10001:req-001", "created", 59900L));

        mockMvc.perform(post("/internal/orders/payment-snapshot")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-payment-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "userId", "10001",
                                "orderId", 101
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_PAYMENT_SNAPSHOT_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-payment-snapshot"))
                .andExpect(jsonPath("$.data.status").value("created"));
    }

    @Test
    void confirmPaidMapsBusinessErrors() throws Exception {
        when(orderPaymentService.confirmPaid(any()))
                .thenThrow(new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409));

        mockMvc.perform(post("/internal/orders/payment-confirmations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "userId", "10001",
                                "orderId", 101,
                                "paymentNo", "PAY-001",
                                "paidAmountCent", 59900
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));
    }

    @Test
    void confirmPaidValidationFailureUsesStableEnvelope() throws Exception {
        mockMvc.perform(post("/internal/orders/payment-confirmations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-paid-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 0,
                                "userId", "",
                                "orderId", 0,
                                "paymentNo", "",
                                "paidAmountCent", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-paid-validation"));
    }
}
