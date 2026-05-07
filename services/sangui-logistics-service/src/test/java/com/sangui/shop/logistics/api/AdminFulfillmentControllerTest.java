package com.sangui.shop.logistics.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.logistics.application.AdminFulfillmentService;
import com.sangui.shop.logistics.client.FulfillmentOrderPageResponse;
import com.sangui.shop.logistics.client.FulfillmentOrderResponse;
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

@WebMvcTest(AdminFulfillmentController.class)
@Import({GlobalApiExceptionHandler.class, AdminFulfillmentControllerTest.ResolverConfig.class})
class AdminFulfillmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminFulfillmentService adminFulfillmentService;

    @Test
    void listFulfillmentsReturnsEnvelope() throws Exception {
        when(adminFulfillmentService.listFulfillments(any(), eq(1), eq(20), eq("unshipped"), eq("ORD"), eq("10001"), any(), any(), any()))
                .thenReturn(new FulfillmentOrderPageResponse(1, 20, 1, List.of(response("paid", "unshipped"))));

        mockMvc.perform(get("/api/admin/fulfillments")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-list")
                        .queryParam("status", "unshipped")
                        .queryParam("orderNo", "ORD")
                        .queryParam("userId", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_FULFILLMENT_LIST"))
                .andExpect(jsonPath("$.data.items[0].fulfillmentStatus").value("unshipped"));
    }

    @Test
    void shipFulfillmentReturnsEnvelopeAndRequestBody() throws Exception {
        when(adminFulfillmentService.shipFulfillment(any(), eq(101L), any(), eq("trace-ship")))
                .thenReturn(response("shipped", "shipped"));

        mockMvc.perform(post("/api/admin/fulfillments/101/ship")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "ship-001",
                                "carrier", "SF Express",
                                "trackingNo", "SF123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_FULFILLMENT_SHIPPED"))
                .andExpect(jsonPath("$.data.status").value("shipped"));

        ArgumentCaptor<SanguiPrincipal> principalCaptor = ArgumentCaptor.forClass(SanguiPrincipal.class);
        verify(adminFulfillmentService).shipFulfillment(principalCaptor.capture(), eq(101L), any(), eq("trace-ship"));
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().shopId()).isEqualTo(1L);
    }

    private SanguiPrincipal principal() {
        return new SanguiPrincipal(
                "90001",
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN),
                "jwt"
        );
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
