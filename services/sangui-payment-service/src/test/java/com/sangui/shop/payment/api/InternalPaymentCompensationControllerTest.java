package com.sangui.shop.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationRecordResponse;
import com.sangui.shop.payment.application.PaymentCompensationOpsService;
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

@WebMvcTest(InternalPaymentCompensationController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalPaymentCompensationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentCompensationOpsService paymentCompensationOpsService;

    @Test
    void queryRecordsReturnsStableEnvelope() throws Exception {
        PaymentCompensationRecordResponse payment = new PaymentCompensationRecordResponse(
                201L,
                "PAY-001",
                101L,
                "ORD-001",
                "10001",
                "mock",
                "created",
                59900L,
                "trace-payment",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "downstream timeout",
                "trace-reconcile",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00")
        );
        when(paymentCompensationOpsService.queryRecords(any()))
                .thenReturn(new PaymentCompensationQueryResponse(1L, List.of(payment), List.of()));

        mockMvc.perform(post("/internal/payments/compensation-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "minAgeMinutes", 1,
                                "limit", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_COMPENSATION_RECORDS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-query"))
                .andExpect(jsonPath("$.data.createdPayments[0].paymentNo").value("PAY-001"));
    }

    @Test
    void manualReconcileReturnsStableEnvelope() throws Exception {
        PaymentCompensationRecordResponse payment = new PaymentCompensationRecordResponse(
                201L,
                "PAY-001",
                101L,
                "ORD-001",
                "10001",
                "mock",
                "paid",
                59900L,
                "trace-payment",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                "settled",
                null,
                null,
                "trace-manual",
                "manual",
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(paymentCompensationOpsService.manualReconcile(any(), any()))
                .thenReturn(new ManualPaymentReconcileResponse("settled", null, null, payment));

        mockMvc.perform(post("/internal/payments/reconciliations/manual")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "paymentNo", "PAY-001",
                                "operator", "ops-user"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECONCILED_MANUALLY"))
                .andExpect(jsonPath("$.data.result").value("settled"))
                .andExpect(jsonPath("$.data.payment.lastCompensationTrigger").value("manual"));
    }

    @Test
    void bulkReconcileReturnsStableEnvelope() throws Exception {
        PaymentCompensationRecordResponse payment = new PaymentCompensationRecordResponse(
                201L,
                "PAY-001",
                101L,
                "ORD-001",
                "10001",
                "mock",
                "created",
                59900L,
                "trace-payment",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "downstream timeout",
                "trace-bulk",
                "manual",
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00")
        );
        when(paymentCompensationOpsService.bulkReconcile(any(), any()))
                .thenReturn(new com.sangui.shop.payment.api.dto.BulkPaymentReconcileResponse(
                        1L,
                        true,
                        1,
                        0,
                        0,
                        1,
                        0,
                        List.of(new com.sangui.shop.payment.api.dto.BulkPaymentReconcileItemResponse(
                                "skipped",
                                "PAYMENT_STATUS_NOT_CREATED",
                                "Payment is no longer in created status.",
                                payment
                        ))
                ));

        mockMvc.perform(post("/internal/payments/reconciliations/bulk")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "dryRun", true,
                                "minAgeMinutes", 1,
                                "limit", 100,
                                "operator", "ops-user"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECONCILED_IN_BULK"))
                .andExpect(jsonPath("$.data.items[0].result").value("skipped"));
    }

    @Test
    void queryRecordsValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/payments/compensation-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "minAgeMinutes", 0,
                                "limit", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-validation"));
    }
}
