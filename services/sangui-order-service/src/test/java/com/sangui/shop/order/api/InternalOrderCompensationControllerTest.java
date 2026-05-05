package com.sangui.shop.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.OpsAuditLogger;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.order.api.dto.OrderCompensationAggregateResponse;
import com.sangui.shop.order.api.dto.OrderCompensationAttemptResponse;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.api.dto.OrderCompensationRecordResponse;
import com.sangui.shop.order.application.OrderCompensationOpsService;
import org.junit.jupiter.api.BeforeEach;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

@WebMvcTest(InternalOrderCompensationController.class)
@Import({GlobalApiExceptionHandler.class, InternalOrderCompensationControllerTest.ResolverConfig.class})
class InternalOrderCompensationControllerTest {

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
    private OrderCompensationOpsService orderCompensationOpsService;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void attachAuditAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(OpsAuditLogger.class);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

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
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        OrderCompensationAttemptResponse attempt = new OrderCompensationAttemptResponse(
                501L,
                101L,
                "ORD-001",
                "ord:10001:req-001",
                "cancelled",
                null,
                null,
                "trace-order-manual",
                "manual",
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(orderCompensationOpsService.queryRecords(any(), any()))
                .thenReturn(new OrderCompensationQueryResponse(
                        1L,
                        1,
                        20,
                        1L,
                        List.of(new OrderCompensationAggregateResponse(
                                order,
                                1L,
                                1L,
                                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                                List.of(attempt)
                        ))
                ));

        mockMvc.perform(post("/internal/orders/compensation-records/query")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "orderId", 101,
                                "result", "cancelled",
                                "pageNo", 1,
                                "pageSize", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_COMPENSATION_RECORDS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-query"))
                .andExpect(jsonPath("$.data.items[0].order.orderNo").value("ORD-001"))
                .andExpect(jsonPath("$.data.items[0].attempts[0].trigger").value("manual"));
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
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(orderCompensationOpsService.manualReplay(any(), any(), any()))
                .thenReturn(new ManualOrderTimeoutReplayResponse("cancelled", null, null, order));

        mockMvc.perform(post("/internal/orders/timeout-replays/manual")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "orderId", 101,
                                "timeoutMinutes", 15,
                                "operator", "ops-user"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_TIMEOUT_REPLAYED_MANUALLY"))
                .andExpect(jsonPath("$.data.result").value("cancelled"))
                .andExpect(jsonPath("$.data.order.lastCompensationTrigger").value("manual"));

        assertThat(auditMessages())
                .contains("action=ops.order.timeout-replay.manual")
                .contains("outcome=success")
                .contains("result=cancelled")
                .contains("targetId=101");
    }

    @Test
    void bulkReplayReturnsStableEnvelope() throws Exception {
        OrderCompensationRecordResponse order = new OrderCompensationRecordResponse(
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "created",
                59900L,
                "trace-order",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                "skipped",
                "ORDER_NOT_TIMEOUT_ELIGIBLE",
                "not yet timed out",
                "trace-order-bulk",
                "manual",
                "ops-user",
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        when(orderCompensationOpsService.bulkReplay(any(), any(), any()))
                .thenReturn(new com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayResponse(
                        1L,
                        true,
                        1,
                        0,
                        0,
                        1,
                        0,
                        List.of(new com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayItemResponse(
                                "skipped",
                                "ORDER_NOT_TIMEOUT_ELIGIBLE",
                                "not yet timed out",
                                order
                        ))
                ));

        mockMvc.perform(post("/internal/orders/timeout-replays/bulk")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "dryRun", true,
                                "timeoutMinutes", 15,
                                "limit", 100,
                                "operator", "ops-user"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_TIMEOUT_REPLAYED_IN_BULK"))
                .andExpect(jsonPath("$.data.items[0].result").value("skipped"));

        assertThat(auditMessages())
                .contains("action=ops.order.timeout-replay.bulk")
                .contains("outcome=success")
                .contains("result=dry-run")
                .contains("targetCount=1")
                .contains("dryRun=true");
    }

    @Test
    void manualReplayValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/orders/timeout-replays/manual")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
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

    @Test
    void queryRecordsValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/orders/compensation-records/query")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-query-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "pageNo", 0,
                                "pageSize", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-query-validation"));
    }

    @Test
    void queryRecordsRequiresTrustedPrincipal() throws Exception {
        mockMvc.perform(post("/internal/orders/compensation-records/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-auth-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "pageNo", 1,
                                "pageSize", 20
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-order-auth-missing"));
    }

    @Test
    void queryForbiddenLogsAuditEvent() throws Exception {
        when(orderCompensationOpsService.queryRecords(any(), any()))
                .thenThrow(new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403));

        mockMvc.perform(post("/internal/orders/compensation-records/query")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "pageNo", 1,
                                "pageSize", 20
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        assertThat(auditMessages())
                .contains("action=ops.order.compensation.query")
                .contains("outcome=denied")
                .contains("errorCode=AUTH_FORBIDDEN");
    }

    private String auditMessages() {
        return auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
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
