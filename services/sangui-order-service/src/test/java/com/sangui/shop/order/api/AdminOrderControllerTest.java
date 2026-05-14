package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.order.api.dto.AdminCancelOrderRequest;
import com.sangui.shop.order.api.dto.AdminOrderDetailResponse;
import com.sangui.shop.order.api.dto.AdminOrderPageResponse;
import com.sangui.shop.order.api.dto.AdminOrderStatusTimelineResponse;
import com.sangui.shop.order.api.dto.AdminOrderSummaryResponse;
import com.sangui.shop.order.api.dto.OrderItemResponse;
import com.sangui.shop.order.application.AdminOrderManagementService;
import com.sangui.shop.order.domain.OrderErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(
        controllers = AdminOrderController.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@Import({GlobalApiExceptionHandler.class, AdminOrderControllerTest.ResolverConfig.class})
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminOrderManagementService adminOrderManagementService;

    @Test
    void listOrdersUsesPrincipalScopeAndParsesFilters() throws Exception {
        when(adminOrderManagementService.listOrders(any(), eq(1), eq(20), eq("created"), eq("ORD"), eq("10001"), any(), any()))
                .thenReturn(new AdminOrderPageResponse(
                        1,
                        20,
                        1,
                        List.of(new AdminOrderSummaryResponse(
                                101L,
                                "ORD-101",
                                1L,
                                "10001",
                                "created",
                                59900L,
                                null,
                                2,
                                "trace-order",
                                OffsetDateTime.parse("2026-05-01T10:00:00+08:00"),
                                OffsetDateTime.parse("2026-05-01T10:00:00+08:00")
                        ))
                ));

        SanguiPrincipal principal = adminPrincipal();
        mockMvc.perform(get("/api/admin/orders")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-list")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .queryParam("status", "created")
                        .queryParam("orderNo", "ORD")
                        .queryParam("userId", "10001")
                        .queryParam("fromTime", "2026-05-01T00:00:00+08:00")
                        .queryParam("toTime", "2026-05-02T00:00:00+08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_ORDER_LIST"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-list"))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("ORD-101"))
                .andExpect(jsonPath("$.data.items[0].itemCount").value(2));

        ArgumentCaptor<SanguiPrincipal> principalCaptor = ArgumentCaptor.forClass(SanguiPrincipal.class);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminOrderManagementService).listOrders(
                principalCaptor.capture(),
                eq(1),
                eq(20),
                eq("created"),
                eq("ORD"),
                eq("10001"),
                fromCaptor.capture(),
                any()
        );
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().shopId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.parse("2026-05-01T00:00:00"));
    }

    @Test
    void detailReturnsAdminOrderEnvelope() throws Exception {
        when(adminOrderManagementService.getOrder(any(), eq(101L)))
                .thenReturn(detail("created"));

        mockMvc.perform(get("/api/admin/orders/101")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_ORDER_DETAIL"))
                .andExpect(jsonPath("$.data.reservationNo").value("ord:10001:req-101"))
                .andExpect(jsonPath("$.data.traceId").value("trace-order"))
                .andExpect(jsonPath("$.data.statusTimeline[0].status").value("created"));
    }

    @Test
    void cancelRequiresRequestIdAndReturnsCancelledDetail() throws Exception {
        when(adminOrderManagementService.cancelOrder(any(), eq(101L), any(), any()))
                .thenReturn(detail("cancelled"));

        mockMvc.perform(post("/api/admin/orders/101/cancel")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestId", "adm-cancel-001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_ORDER_CANCELLED"))
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        ArgumentCaptor<AdminCancelOrderRequest> requestCaptor = ArgumentCaptor.forClass(AdminCancelOrderRequest.class);
        verify(adminOrderManagementService).cancelOrder(any(), eq(101L), requestCaptor.capture(), eq("trace-admin-cancel"));
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().requestId()).isEqualTo("adm-cancel-001");
    }

    @Test
    void mapsInvalidStatusTransition() throws Exception {
        when(adminOrderManagementService.cancelOrder(any(), eq(101L), any(), any()))
                .thenThrow(new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409));

        mockMvc.perform(post("/api/admin/orders/101/cancel")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestId", "adm-cancel-001"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-invalid"));
    }

    private SanguiPrincipal adminPrincipal() {
        return new SanguiPrincipal(
                "90001",
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN),
                "jwt-admin"
        );
    }

    private AdminOrderDetailResponse detail(String status) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-01T10:00:00+08:00");
        return new AdminOrderDetailResponse(
                101L,
                "ORD-101",
                1L,
                "10001",
                "req-101",
                "ord:10001:req-101",
                null,
                status,
                59900L,
                "trace-order",
                timestamp,
                timestamp,
                List.of(new OrderItemResponse(301L, 401L, "Sneaker 42", 59900L, 1, 59900L)),
                List.of(new AdminOrderStatusTimelineResponse("created", timestamp, "trace-order"))
        );
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
