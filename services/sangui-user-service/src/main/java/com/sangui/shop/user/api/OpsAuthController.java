package com.sangui.shop.user.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.OpsAuditLogger;
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
        try {
            OpsSessionResponse response = opsAuthService.login(request);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.auth.login")
                    .outcome("success")
                    .result("issued")
                    .shopId(request.shopId())
                    .userId(String.valueOf(response.userId()))
                    .username(response.username())
                    .userIdentifier(request.usernameOrMobile())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .build());
            return ApiResult.ok("OPS_USER_LOGGED_IN", response, OpsAuditLogger.traceId(httpRequest));
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.auth.login")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .result("rejected")
                    .shopId(request.shopId())
                    .userIdentifier(request.usernameOrMobile())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }

    @PostMapping("/session/refresh")
    public ApiResult<OpsSessionResponse> refresh(
            SanguiPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        try {
            OpsSessionResponse response = opsAuthService.refresh(principal);
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.auth.refresh")
                    .outcome("success")
                    .result("issued")
                    .username(response.username())
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .build());
            return ApiResult.ok("OPS_SESSION_REFRESHED", response, OpsAuditLogger.traceId(httpRequest));
        } catch (RuntimeException exception) {
            OpsAuditLogger.log(httpRequest, OpsAuditLogger.event(httpRequest, "ops.auth.refresh")
                    .outcome(OpsAuditLogger.outcome(exception))
                    .result("rejected")
                    .permission(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)
                    .errorCode(OpsAuditLogger.errorCode(exception))
                    .reason(OpsAuditLogger.reason(exception))
                    .build());
            throw exception;
        }
    }
}
