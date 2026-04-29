package com.sangui.shop.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.user.api.dto.LoginUserResponse;
import com.sangui.shop.user.api.dto.RegisterUserResponse;
import com.sangui.shop.user.application.UserAuthService;
import com.sangui.shop.user.domain.UserErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserAuthController.class)
@Import(GlobalApiExceptionHandler.class)
class UserAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAuthService userAuthService;

    @Test
    void registerReturnsApiResultEnvelope() throws Exception {
        when(userAuthService.register(any()))
                .thenReturn(new RegisterUserResponse(10001L, 1L, "alice", "13800000000", List.of("USER")));

        mockMvc.perform(post("/api/users/register")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "username", "alice",
                                "mobile", "13800000000",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.traceId").value("trace-register"))
                .andExpect(jsonPath("$.data.userId").value(10001))
                .andExpect(jsonPath("$.data.shopId").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.mobile").value("13800000000"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void registerValidationFailureUsesStableErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "username", "a",
                                "mobile", "not-mobile",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-validation"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void duplicateRegistrationMapsToConflict() throws Exception {
        when(userAuthService.register(any()))
                .thenThrow(new SanguiException(UserErrorCode.USER_USERNAME_EXISTS, 409));

        mockMvc.perform(post("/api/users/register")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "username", "alice",
                                "mobile", "13800000000",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_USERNAME_EXISTS"))
                .andExpect(jsonPath("$.traceId").value("trace-duplicate"));
    }

    @Test
    void loginReturnsBearerTokenEnvelope() throws Exception {
        when(userAuthService.login(any()))
                .thenReturn(new LoginUserResponse(10001L, 1L, "jwt-token", "Bearer", 7200, List.of("USER")));

        mockMvc.perform(post("/api/users/login")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "usernameOrMobile", "alice",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Authorization"))
                .andExpect(jsonPath("$.code").value("USER_LOGGED_IN"))
                .andExpect(jsonPath("$.traceId").value("trace-login"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200));
    }

    @Test
    void invalidLoginMapsToUnauthorized() throws Exception {
        when(userAuthService.login(any()))
                .thenThrow(new SanguiException(UserErrorCode.AUTH_INVALID_CREDENTIALS, 401));

        mockMvc.perform(post("/api/users/login")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-login-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "usernameOrMobile", "alice",
                                "password", "Passw0rd!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").value("trace-login-fail"));
    }
}
