package com.sangui.shop.common.web;

import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.security.SanguiPrincipalHeaderParser;
import com.sangui.shop.common.security.SanguiSecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

public class SanguiAuthenticationContextFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = SanguiPrincipal.class.getName();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SanguiSecurityContext.clear();
        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(request::getHeader);
        principal.ifPresent(value -> {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, value);
            SanguiSecurityContext.setPrincipal(value);
        });
        try {
            filterChain.doFilter(request, response);
        } finally {
            SanguiSecurityContext.clear();
        }
    }
}
