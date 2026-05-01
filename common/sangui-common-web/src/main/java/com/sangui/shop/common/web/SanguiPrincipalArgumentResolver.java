package com.sangui.shop.common.web;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.security.SanguiSecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class SanguiPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (SanguiPrincipal.class.equals(parameter.getParameterType())) {
            return true;
        }
        if (!Optional.class.equals(parameter.getParameterType())) {
            return false;
        }
        Type type = parameter.getGenericParameterType();
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        return arguments.length == 1 && SanguiPrincipal.class.equals(arguments[0]);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Optional<SanguiPrincipal> principal = principal(webRequest);
        if (Optional.class.equals(parameter.getParameterType())) {
            return principal;
        }
        return principal.orElseThrow(() -> new SanguiException(CommonErrorCode.AUTH_TOKEN_MISSING, 401));
    }

    private Optional<SanguiPrincipal> principal(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request != null) {
            Object attribute = request.getAttribute(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE);
            if (attribute instanceof SanguiPrincipal principal) {
                return Optional.of(principal);
            }
        }
        return SanguiSecurityContext.currentPrincipal();
    }
}
