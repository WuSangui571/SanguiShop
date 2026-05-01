package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.api.dto.CreateOrderRequest;
import com.sangui.shop.order.api.dto.OrderItemResponse;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.application.OrderCreateService;
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

@WebMvcTest(OrderController.class)
@Import({GlobalApiExceptionHandler.class, OrderControllerTest.ResolverConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderCreateService orderCreateService;

    @Test
    void createOrderUsesPrincipalParameterInsteadOfBodyIdentity() throws Exception {
        when(orderCreateService.createOrder(any(), any(), any()))
                .thenReturn(new OrderResponse(
                        101L,
                        "ORDTEST0001",
                        1L,
                        "10001",
                        "req-001",
                        "created",
                        59900L,
                        List.of(new OrderItemResponse(301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
                ));

        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/orders")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 999,
                                "userId", "spoof-user",
                                "requestId", "req-001",
                                "items", List.of(Map.of(
                                        "skuId", 401,
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.traceId").value("trace-create-order"))
                .andExpect(jsonPath("$.data.orderNo").value("ORDTEST0001"))
                .andExpect(jsonPath("$.data.userId").value("10001"));

        ArgumentCaptor<SanguiPrincipal> principalCaptor = ArgumentCaptor.forClass(SanguiPrincipal.class);
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderCreateService).createOrder(principalCaptor.capture(), requestCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().shopId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().userId()).isEqualTo("10001");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().shopId()).isEqualTo(999L);
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().userId()).isEqualTo("spoof-user");
    }

    @Test
    void createOrderRequiresTrustedPrincipal() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-no-principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "req-001",
                                "items", List.of(Map.of(
                                        "skuId", 401,
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-no-principal"));
    }

    @Test
    void createOrderValidationFailureUsesStableErrorEnvelope() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/orders")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "",
                                "items", List.of()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-order-validation"));
    }

    @Test
    void createOrderMapsServiceErrors() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        when(orderCreateService.createOrder(any(), any(), any()))
                .thenThrow(new SanguiException(com.sangui.shop.order.domain.OrderErrorCode.ORDER_SKU_NOT_FOUND, 404));

        mockMvc.perform(post("/api/orders")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-order-missing-sku")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "req-001",
                                "items", List.of(Map.of(
                                        "skuId", 401,
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_SKU_NOT_FOUND"));
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
