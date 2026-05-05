package com.sangui.shop.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.OpsAuditLogger;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.user.api.dto.OpsSessionResponse;
import com.sangui.shop.user.application.OpsAuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OpsAuthControllerTest {

    private static final SanguiPrincipal ADMIN_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of(),
            java.util.Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN),
            "jwt-ops-1"
    );

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OpsAuthService opsAuthService;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void setUp() {
        opsAuthService = mock(OpsAuthService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new OpsAuthController(opsAuthService))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .setCustomArgumentResolvers(new SanguiPrincipalArgumentResolver())
                .build();
        Logger auditLogger = (Logger) LoggerFactory.getLogger(OpsAuditLogger.class);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @Test
    void loginReturnsOpsSessionEnvelope() throws Exception {
        when(opsAuthService.login(any()))
                .thenReturn(new OpsSessionResponse(
                        10001L,
                        1L,
                        "ops-admin",
                        "jwt-admin-token",
                        "Bearer",
                        3600,
                        List.of(),
                        List.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                ));

        mockMvc.perform(post("/api/users/ops/login")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ops-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "usernameOrMobile", "ops-admin",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Authorization"))
                .andExpect(jsonPath("$.code").value("OPS_USER_LOGGED_IN"))
                .andExpect(jsonPath("$.traceId").value("trace-ops-login"))
                .andExpect(jsonPath("$.data.username").value("ops-admin"))
                .andExpect(jsonPath("$.data.permissions[0]").value(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN));

        assertThat(auditMessages())
                .contains("action=ops.auth.login")
                .contains("outcome=success")
                .contains("result=issued")
                .contains("userIdentifier=ops-admin")
                .contains("permission=OPS_COMPENSATION_ADMIN");
    }

    @Test
    void loginForbiddenMapsToForbiddenEnvelope() throws Exception {
        when(opsAuthService.login(any()))
                .thenThrow(new SanguiException(com.sangui.shop.common.core.error.CommonErrorCode.AUTH_FORBIDDEN, 403));

        mockMvc.perform(post("/api/users/ops/login")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ops-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "usernameOrMobile", "alice",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").value("trace-ops-forbidden"));

        assertThat(auditMessages())
                .contains("action=ops.auth.login")
                .contains("outcome=denied")
                .contains("errorCode=AUTH_FORBIDDEN")
                .contains("userIdentifier=alice");
    }

    @Test
    void refreshReturnsOpsSessionEnvelope() throws Exception {
        when(opsAuthService.refresh(any()))
                .thenReturn(new OpsSessionResponse(
                        10001L,
                        1L,
                        "ops-admin",
                        "jwt-admin-token-2",
                        "Bearer",
                        3600,
                        List.of(),
                        List.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                ));

        mockMvc.perform(post("/api/users/ops/session/refresh")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ops-refresh")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, ADMIN_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OPS_SESSION_REFRESHED"))
                .andExpect(jsonPath("$.traceId").value("trace-ops-refresh"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-admin-token-2"));

        assertThat(auditMessages())
                .contains("action=ops.auth.refresh")
                .contains("outcome=success")
                .contains("result=issued")
                .contains("jwtId=jwt-ops-1");
    }

    @Test
    void refreshRequiresAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(post("/api/users/ops/session/refresh")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-ops-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-ops-missing"));

        assertThat(auditMessages())
                .contains("action=ops.auth.refresh")
                .contains("outcome=failed")
                .contains("errorCode=AUTH_TOKEN_MISSING");
    }

    private String auditMessages() {
        return auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
