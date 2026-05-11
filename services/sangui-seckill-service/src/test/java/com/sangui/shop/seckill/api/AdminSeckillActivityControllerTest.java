package com.sangui.shop.seckill.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityDetailResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityPageResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivitySkuResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivitySummaryResponse;
import com.sangui.shop.seckill.application.AdminSeckillActivityService;
import com.sangui.shop.seckill.domain.SeckillErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(
        controllers = AdminSeckillActivityController.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
@Import({GlobalApiExceptionHandler.class, AdminSeckillActivityControllerTest.ResolverConfig.class})
class AdminSeckillActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AdminSeckillActivityService adminSeckillActivityService;

    private final OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-11T18:30:00+08:00");

    @Test
    void listReturnsEnvelopeWithCodeMessageTraceIdTimestamp() throws Exception {
        when(adminSeckillActivityService.listActivities(any(), eq(1), eq(20), eq("draft")))
                .thenReturn(new AdminSeckillActivityPageResponse(
                        1, 20, 1,
                        List.of(new AdminSeckillActivitySummaryResponse(
                                9001L, "Spring flash sale", "draft",
                                timestamp, timestamp.plusHours(2), timestamp,
                                1, 10, 0
                        ))
                ));

        mockMvc.perform(get("/api/admin/seckill/activities")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-list")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .queryParam("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_LIST"))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.traceId").value("trace-list"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].activityName").value("Spring flash sale"))
                .andExpect(jsonPath("$.data.items[0].skuCount").value(1));
    }

    @Test
    void detailReturnsEnvelope() throws Exception {
        when(adminSeckillActivityService.getActivity(any(), eq(9001L)))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/admin/seckill/activities/9001")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_DETAIL"))
                .andExpect(jsonPath("$.data.activityId").value(9001))
                .andExpect(jsonPath("$.data.skus[0].skuId").value(401));
    }

    @Test
    void createReturnsCreatedEnvelope() throws Exception {
        when(adminSeckillActivityService.createActivity(any(), any(), any()))
                .thenReturn(detailResponse());

        mockMvc.perform(post("/api/admin/seckill/activities")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityName", "Spring flash sale",
                                "startsAt", "2026-05-12T10:00:00+08:00",
                                "endsAt", "2026-05-12T12:00:00+08:00",
                                "requestId", "req-001",
                                "skus", List.of(Map.of("productId", 301, "skuId", 401, "activityStock", 10, "seckillPriceCent", 49900))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_CREATED"))
                .andExpect(jsonPath("$.data.activityName").value("Spring flash sale"));
    }

    @Test
    void updateReturnsUpdatedEnvelope() throws Exception {
        when(adminSeckillActivityService.updateActivity(any(), eq(9001L), any(), any()))
                .thenReturn(detailResponse());

        mockMvc.perform(put("/api/admin/seckill/activities/9001")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityName", "Updated sale",
                                "startsAt", "2026-05-12T10:00:00+08:00",
                                "endsAt", "2026-05-12T12:00:00+08:00",
                                "requestId", "req-upd"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_UPDATED"));
    }

    @Test
    void statusUpdateReturnsStatusUpdatedEnvelope() throws Exception {
        when(adminSeckillActivityService.updateStatus(any(), eq(9001L), any(), any()))
                .thenReturn(detailResponse());

        mockMvc.perform(post("/api/admin/seckill/activities/9001/status")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "scheduled", "requestId", "req-st"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED"));
    }

    @Test
    void skuBindReturnsBoundEnvelope() throws Exception {
        when(adminSeckillActivityService.bindSku(any(), eq(9001L), any(), any()))
                .thenReturn(detailResponse());

        mockMvc.perform(post("/api/admin/seckill/activities/9001/skus")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-sku")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", 301, "skuId", 401,
                                "activityStock", 10, "seckillPriceCent", 49900,
                                "requestId", "req-sku"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_SECKILL_ACTIVITY_SKU_BOUND"));
    }

    @Test
    void validationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/seckill/activities")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-val")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityName", "",
                                "startsAt", "invalid",
                                "endsAt", "2026-05-12T12:00:00+08:00",
                                "requestId", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void activityNotFoundReturns404() throws Exception {
        when(adminSeckillActivityService.getActivity(any(), eq(9999L)))
                .thenThrow(new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));

        mockMvc.perform(get("/api/admin/seckill/activities/9999")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECKILL_ACTIVITY_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-404"));
    }

    @Test
    void invalidStatusTransitionReturns409() throws Exception {
        when(adminSeckillActivityService.updateStatus(any(), eq(9001L), any(), any()))
                .thenThrow(new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID, 409));

        mockMvc.perform(post("/api/admin/seckill/activities/9001/status")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-409")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ended", "requestId", "req-inv"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECKILL_ACTIVITY_STATUS_INVALID"))
                .andExpect(jsonPath("$.traceId").value("trace-409"));
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        when(adminSeckillActivityService.createActivity(any(), any(), any()))
                .thenThrow(new SanguiException(com.sangui.shop.common.core.error.CommonErrorCode.IDEMPOTENCY_CONFLICT, 409));

        mockMvc.perform(post("/api/admin/seckill/activities")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, adminPrincipal())
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityName", "Duplicate",
                                "startsAt", "2026-05-12T10:00:00+08:00",
                                "endsAt", "2026-05-12T12:00:00+08:00",
                                "requestId", "req-existing"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private SanguiPrincipal adminPrincipal() {
        return new SanguiPrincipal("90001", 1L, Set.of(),
                Set.of(com.sangui.shop.common.security.SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN),
                "jwt-admin");
    }

    private AdminSeckillActivityDetailResponse detailResponse() {
        return new AdminSeckillActivityDetailResponse(
                9001L, "Spring flash sale", null, "draft",
                timestamp, timestamp.plusHours(2), timestamp,
                1, 10, 0,
                List.of(new AdminSeckillActivitySkuResponse(
                        301L, "Running Shoe", 401L, "RS-42", "42",
                        59900L, 49900L, 20L, 10L, 0L
                ))
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
