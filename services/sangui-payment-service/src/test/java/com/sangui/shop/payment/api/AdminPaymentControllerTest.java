package com.sangui.shop.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.application.PaymentPayService;
import com.sangui.shop.payment.domain.PaymentErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(AdminPaymentController.class)
@Import({GlobalApiExceptionHandler.class, AdminPaymentControllerTest.ResolverConfig.class})
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentPayService paymentPayService;

    @Test
    void getsAdminPaymentStatusByOrderId() throws Exception {
        when(paymentPayService.getAdminPaymentByOrderId(any(), eq(101L)))
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

        mockMvc.perform(get("/api/admin/payments/by-order/101")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_PAYMENT_STATUS"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-payment"))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-001"));

        verify(paymentPayService).getAdminPaymentByOrderId(any(), eq(101L));
    }

    @Test
    void mapsMissingPaymentForOrder() throws Exception {
        when(paymentPayService.getAdminPaymentByOrderId(any(), eq(101L)))
                .thenThrow(new SanguiException(PaymentErrorCode.PAYMENT_NOT_FOUND, 404));

        mockMvc.perform(get("/api/admin/payments/by-order/101")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-payment-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-payment-missing"));
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
