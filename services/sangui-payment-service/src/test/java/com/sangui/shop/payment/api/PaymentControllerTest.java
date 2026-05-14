package com.sangui.shop.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.payment.api.dto.CreatePaymentRequest;
import com.sangui.shop.payment.api.dto.PaymentCallbackResponse;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.application.PaymentCallbackService;
import com.sangui.shop.payment.application.PaymentPayService;
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
        controllers = PaymentController.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@Import({GlobalApiExceptionHandler.class, PaymentControllerTest.ResolverConfig.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentPayService paymentPayService;

    @MockBean
    private PaymentCallbackService paymentCallbackService;

    @Test
    void createPaymentUsesPrincipalInsteadOfBodyIdentity() throws Exception {
        when(paymentPayService.pay(any(), any(), any()))
                .thenReturn(new PaymentResponse(
                        201L,
                        "PAY-001",
                        101L,
                        "ORD-001",
                        1L,
                        "10001",
                        "mock",
                        "paid",
                        59900L
                ));

        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/payments")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 999,
                                "userId", "spoof-user",
                                "orderId", 101,
                                "paymentNo", "PAY-001",
                                "channel", "mock"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_PAID"))
                .andExpect(jsonPath("$.traceId").value("trace-payment"))
                .andExpect(jsonPath("$.data.userId").value("10001"));

        ArgumentCaptor<SanguiPrincipal> principalCaptor = ArgumentCaptor.forClass(SanguiPrincipal.class);
        ArgumentCaptor<CreatePaymentRequest> requestCaptor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentPayService).pay(principalCaptor.capture(), requestCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().shopId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().userId()).isEqualTo("10001");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().shopId()).isEqualTo(999L);
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().userId()).isEqualTo("spoof-user");
    }

    @Test
    void createPaymentRequiresTrustedPrincipal() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-no-principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 101,
                                "paymentNo", "PAY-001",
                                "channel", "mock"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-no-principal"));
    }

    @Test
    void createPaymentValidationFailureUsesStableEnvelope() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/payments")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 0,
                                "paymentNo", "",
                                "channel", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-payment-validation"));
    }

    @Test
    void createPaymentMapsServiceErrors() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        when(paymentPayService.pay(any(), any(), any()))
                .thenThrow(new SanguiException(com.sangui.shop.payment.domain.PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID, 409));

        mockMvc.perform(post("/api/payments")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 101,
                                "paymentNo", "PAY-001",
                                "channel", "mock"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_STATUS_INVALID"));
    }

    @Test
    void getPaymentReturnsCurrentStatusForPrincipal() throws Exception {
        when(paymentPayService.getPayment(any(), any()))
                .thenReturn(new PaymentResponse(
                        201L,
                        "PAY-001",
                        101L,
                        "ORD-001",
                        1L,
                        "10001",
                        "mock",
                        "paid",
                        59900L
                ));

        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(get("/api/payments/PAY-001")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATUS"))
                .andExpect(jsonPath("$.data.status").value("paid"));
    }

    @Test
    void mockCallbackRecordsAndProcessesProviderEvent() throws Exception {
        when(paymentCallbackService.handleCallback(any(), any()))
                .thenReturn(new PaymentCallbackResponse(
                        "PAY-001",
                        "mock",
                        "MOCK-TXN-001",
                        "paid",
                        "processed"
                ));

        mockMvc.perform(post("/api/payments/callbacks/mock")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-payment-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "paymentNo", "PAY-001",
                                "channel", "mock",
                                "channelTradeNo", "MOCK-TXN-001",
                                "tradeStatus", "SUCCESS",
                                "paidAmountCent", 59900
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_CALLBACK_PROCESSED"))
                .andExpect(jsonPath("$.data.processStatus").value("processed"));
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
