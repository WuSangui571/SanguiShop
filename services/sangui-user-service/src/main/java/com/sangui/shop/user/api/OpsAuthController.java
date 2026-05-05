package com.sangui.shop.user.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.OpsSessionResponse;
import com.sangui.shop.user.application.OpsAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/ops")
public class OpsAuthController {

    private final OpsAuthService opsAuthService;

    public OpsAuthController(OpsAuthService opsAuthService) {
        this.opsAuthService = opsAuthService;
    }

    @PostMapping("/login")
    public ApiResult<OpsSessionResponse> login(
            @Valid @RequestBody LoginUserRequest request,
            HttpServletRequest httpRequest
    ) {
        OpsSessionResponse response = opsAuthService.login(request);
        return ApiResult.ok("OPS_USER_LOGGED_IN", response, traceId(httpRequest));
    }

    @PostMapping("/session/refresh")
    public ApiResult<OpsSessionResponse> refresh(
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        OpsSessionResponse response = opsAuthService.refresh(principal);
        return ApiResult.ok("OPS_SESSION_REFRESHED", response, traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
