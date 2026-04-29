package com.sangui.shop.user.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.LoginUserResponse;
import com.sangui.shop.user.api.dto.RegisterUserRequest;
import com.sangui.shop.user.api.dto.RegisterUserResponse;
import com.sangui.shop.user.application.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAuthController {

    private final UserAuthService userAuthService;

    public UserAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/register")
    public ApiResult<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest httpRequest
    ) {
        RegisterUserResponse response = userAuthService.register(request);
        return ApiResult.ok("USER_REGISTERED", response, traceId(httpRequest));
    }

    @PostMapping("/login")
    public ApiResult<LoginUserResponse> login(
            @Valid @RequestBody LoginUserRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginUserResponse response = userAuthService.login(request);
        return ApiResult.ok("USER_LOGGED_IN", response, traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
