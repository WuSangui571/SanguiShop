package com.sangui.shop.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiIdentityHeaderNames;
import com.sangui.shop.common.security.SanguiPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class SanguiPrincipalArgumentResolverTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PrincipalController())
            .setCustomArgumentResolvers(new SanguiPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalApiExceptionHandler())
            .addFilters(new TraceIdFilter(), new SanguiAuthenticationContextFilter())
            .build();

    @Test
    void controllerReceivesRequiredPrincipalFromTrustedHeaders() throws Exception {
        mockMvc.perform(get("/principal")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-principal")
                        .header(SanguiIdentityHeaderNames.USER_ID, "10001")
                        .header(SanguiIdentityHeaderNames.SHOP_ID, "1")
                        .header(SanguiIdentityHeaderNames.ROLES, "USER,ADMIN")
                        .header(SanguiIdentityHeaderNames.PERMISSIONS, "order:create")
                        .header(SanguiIdentityHeaderNames.JWT_ID, "jwt-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_RESOLVED"))
                .andExpect(jsonPath("$.traceId").value("trace-principal"))
                .andExpect(jsonPath("$.data.userId").value("10001"))
                .andExpect(jsonPath("$.data.shopId").value(1))
                .andExpect(jsonPath("$.data.jwtId").value("jwt-001"));
    }

    @Test
    void requiredPrincipalRejectsMissingTrustedHeadersAndIgnoresQueryFields() throws Exception {
        mockMvc.perform(get("/principal")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-missing")
                        .queryParam("userId", "query-user")
                        .queryParam("shopId", "99"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-missing"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void optionalPrincipalCanBeEmptyForPublicHandlers() throws Exception {
        mockMvc.perform(get("/optional-principal")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-optional"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OPTIONAL_PRINCIPAL_RESOLVED"))
                .andExpect(jsonPath("$.data.present").value(false));
    }

    @RestController
    private static final class PrincipalController {

        @GetMapping("/principal")
        ApiResult<Map<String, Object>> principal(SanguiPrincipal principal, HttpServletRequest request) {
            return ApiResult.ok("PRINCIPAL_RESOLVED", Map.of(
                    "userId", principal.userId(),
                    "shopId", principal.shopId(),
                    "jwtId", principal.jwtId()
            ), traceId(request));
        }

        @GetMapping("/optional-principal")
        ApiResult<Map<String, Object>> optionalPrincipal(
                Optional<SanguiPrincipal> principal,
                HttpServletRequest request
        ) {
            return ApiResult.ok("OPTIONAL_PRINCIPAL_RESOLVED", Map.of(
                    "present", principal.isPresent()
            ), traceId(request));
        }

        private String traceId(HttpServletRequest request) {
            return (String) request.getAttribute(TraceConstants.TRACE_ID);
        }
    }
}
