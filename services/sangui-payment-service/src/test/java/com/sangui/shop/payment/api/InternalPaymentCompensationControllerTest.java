package com.sangui.shop.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationAggregateResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationAttemptResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationRecordResponse;
import com.sangui.shop.payment.application.PaymentCompensationOpsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(InternalPaymentCompensationController.class)
@Import({GlobalApiExceptionHandler.class, InternalPaymentCompensationControllerTest.ResolverConfig.class})
class InternalPaymentCompensationControllerTest {

    private static final SanguiPrincipal ADMIN_PRINCIPAL = new SanguiPrincipal(
            "ops-admin",
            1L,
            java.util.Set.of("ADMIN"),
            java.util.Set.of(),
            "jwt-ops-1"
    );

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
        PaymentCompensationAttemptResponse attempt = new PaymentCompensationAttemptResponse(
                601L,
                201L,
                101L,
                "PAY-001",
                "ORD-001",
                "ord:10001:req-001",
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "downstream timeout",
                "trace-reconcile",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00")
        );
        when(paymentCompensationOpsService.queryRecords(any(), any()))
                .thenReturn(new PaymentCompensationQueryResponse(
                        1L,
                        1,
                        20,
                        1L,
                        List.of(new PaymentCompensationAggregateResponse(
                                payment,
                                1L,
                                1L,
                                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                                List.of(attempt)
                        ))
                ));

        mockMvc.perform(post("/internal/payments/compensation-records/query")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "paymentNo", "PAY-001",
                                "result", "failed",
                                "pageNo", 1,
                                "pageSize", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_COMPENSATION_RECORDS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-query"))
                .andExpect(jsonPath("$.data.items[0].payment.paymentNo").value("PAY-001"))
                .andExpect(jsonPath("$.data.items[0].attempts[0].trigger").value("scheduler"));
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
        when(paymentCompensationOpsService.manualReconcile(any(), any(), any()))
                .thenReturn(new ManualPaymentReconcileResponse("settled", null, null, payment));

        mockMvc.perform(post("/internal/payments/reconciliations/manual")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
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
        when(paymentCompensationOpsService.bulkReconcile(any(), any(), any()))
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
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
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
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "pageNo", 0,
                                "pageSize", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-validation"));
    }

    @Test
    void queryRecordsRequiresTrustedPrincipal() throws Exception {
        mockMvc.perform(post("/internal/payments/compensation-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-auth-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "pageNo", 1,
                                "pageSize", 20
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-auth-missing"));
    }

    @TestConfiguration
    static class ResolverConfig {

        @Bean
        WebMvcConfigurer sanguiPrincipalResolverConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new SanguiPrincipalArgumentResolver());
                }
            };
        }
    }
}
