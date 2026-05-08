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
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.order.api.dto.AdminReviewPageResponse;
import com.sangui.shop.order.api.dto.AdminReviewReplyRequest;
import com.sangui.shop.order.api.dto.AdminReviewSummaryResponse;
import com.sangui.shop.order.api.dto.AdminReviewVisibilityRequest;
import com.sangui.shop.order.application.AdminReviewManagementService;
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

@WebMvcTest(AdminReviewController.class)
@Import({GlobalApiExceptionHandler.class, AdminReviewControllerTest.ResolverConfig.class})
class AdminReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminReviewManagementService adminReviewManagementService;

    @Test
    void listReviewsParsesFiltersAndReturnsEnvelope() throws Exception {
        when(adminReviewManagementService.listReviews(any(), eq(1), eq(20), eq(301L), eq(5), eq("10001"), eq("visible"), any(), any()))
                .thenReturn(new AdminReviewPageResponse(
                        1,
                        20,
                        1,
                        List.of(review("visible"))
                ));

        mockMvc.perform(get("/api/admin/reviews")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-review-list")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .queryParam("productId", "301")
                        .queryParam("rating", "5")
                        .queryParam("userId", "10001")
                        .queryParam("visibility", "visible")
                        .queryParam("fromTime", "2026-05-08T00:00:00+08:00")
                        .queryParam("toTime", "2026-05-09T00:00:00+08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_REVIEW_LIST"))
                .andExpect(jsonPath("$.traceId").value("trace-review-list"))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(9001));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminReviewManagementService).listReviews(
                any(),
                eq(1),
                eq(20),
                eq(301L),
                eq(5),
                eq("10001"),
                eq("visible"),
                fromCaptor.capture(),
                any()
        );
        org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.parse("2026-05-08T00:00:00"));
    }

    @Test
    void updateVisibilityRequiresRequestIdAndReturnsUpdatedEnvelope() throws Exception {
        when(adminReviewManagementService.updateVisibility(any(), eq(9001L), any(), eq("trace-review-vis")))
                .thenReturn(review("hidden"));

        mockMvc.perform(post("/api/admin/reviews/9001/visibility")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-review-vis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "visibility", "hidden",
                                "reason", "Contains sensitive content",
                                "requestId", "vis-001"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_REVIEW_VISIBILITY_UPDATED"))
                .andExpect(jsonPath("$.data.visibilityStatus").value("hidden"));

        ArgumentCaptor<AdminReviewVisibilityRequest> requestCaptor = ArgumentCaptor.forClass(AdminReviewVisibilityRequest.class);
        verify(adminReviewManagementService).updateVisibility(any(), eq(9001L), requestCaptor.capture(), eq("trace-review-vis"));
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().requestId()).isEqualTo("vis-001");
    }

    @Test
    void upsertReplyRequiresContentAndReturnsUpdatedEnvelope() throws Exception {
        when(adminReviewManagementService.upsertReply(any(), eq(9001L), any(), eq("trace-review-reply")))
                .thenReturn(review("visible"));

        mockMvc.perform(post("/api/admin/reviews/9001/reply")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-review-reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Thanks for the feedback.",
                                "requestId", "reply-001"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_REVIEW_REPLIED"));

        ArgumentCaptor<AdminReviewReplyRequest> requestCaptor = ArgumentCaptor.forClass(AdminReviewReplyRequest.class);
        verify(adminReviewManagementService).upsertReply(any(), eq(9001L), requestCaptor.capture(), eq("trace-review-reply"));
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().requestId()).isEqualTo("reply-001");
    }

    private SanguiPrincipal adminPrincipal() {
        return new SanguiPrincipal(
                "90001",
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.REVIEW_MANAGEMENT_ADMIN),
                "jwt-admin"
        );
    }

    private AdminReviewSummaryResponse review(String visibility) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-08T10:00:00+08:00");
        return new AdminReviewSummaryResponse(
                9001L,
                101L,
                "ORD-101",
                301L,
                401L,
                "Sneaker 42",
                5,
                "Great",
                2,
                "10***01",
                visibility,
                "Contains sensitive content",
                "vis-001",
                "90001",
                "trace-review-vis",
                timestamp,
                "Thanks for the feedback.",
                "visible",
                "reply-001",
                "90001",
                "trace-review-reply",
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
