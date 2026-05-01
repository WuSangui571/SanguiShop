package com.sangui.shop.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.common.security.SanguiIdentityHeaderNames;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.security.SanguiSecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SanguiAuthenticationContextFilterTest {

    private final SanguiAuthenticationContextFilter filter = new SanguiAuthenticationContextFilter();

    @AfterEach
    void clearContext() {
        SanguiSecurityContext.clear();
    }

    @Test
    void trustedHeadersBindRequestAttributeAndCurrentPrincipal() throws ServletException, IOException {
        MockHttpServletRequest request = requestWithTrustedHeaders();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SanguiPrincipal> currentDuringChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                currentDuringChain.set(SanguiSecurityContext.currentPrincipal().orElseThrow());

        filter.doFilter(request, response, chain);

        Object attribute = request.getAttribute(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(attribute).isInstanceOf(SanguiPrincipal.class);
        assertThat(((SanguiPrincipal) attribute).userId()).isEqualTo("10001");
        assertThat(currentDuringChain.get().shopId()).isEqualTo(1L);
        assertThat(currentDuringChain.get().jwtId()).isEqualTo("jwt-001");
        assertThat(SanguiSecurityContext.currentPrincipal()).isEmpty();
    }

    @Test
    void missingIdentityHeadersLeaveEmptyContextAndIgnoreBodyFields() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("""
                {"userId":"body-user","shopId":99}
                """.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> emptyDuringChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                emptyDuringChain.set(SanguiSecurityContext.currentPrincipal().isEmpty());

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE)).isNull();
        assertThat(emptyDuringChain.get()).isTrue();
        assertThat(SanguiSecurityContext.currentPrincipal()).isEmpty();
    }

    @Test
    void invalidShopIdClearsPreviouslyBoundContext() throws ServletException, IOException {
        SanguiSecurityContext.setPrincipal(new SanguiPrincipal("stale", 9L, Set.of("USER"), Set.of(), "stale-jwt"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SanguiIdentityHeaderNames.USER_ID, "10001");
        request.addHeader(SanguiIdentityHeaderNames.SHOP_ID, "invalid");
        request.addHeader(SanguiIdentityHeaderNames.ROLES, "USER,ADMIN");
        request.addHeader(SanguiIdentityHeaderNames.PERMISSIONS, "order:create");
        request.addHeader(SanguiIdentityHeaderNames.JWT_ID, "jwt-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> emptyDuringChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                emptyDuringChain.set(SanguiSecurityContext.currentPrincipal().isEmpty());

        filter.doFilter(request, response, chain);

        assertThat(emptyDuringChain.get()).isTrue();
        assertThat(SanguiSecurityContext.currentPrincipal()).isEmpty();
    }

    private MockHttpServletRequest requestWithTrustedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SanguiIdentityHeaderNames.USER_ID, "10001");
        request.addHeader(SanguiIdentityHeaderNames.SHOP_ID, "1");
        request.addHeader(SanguiIdentityHeaderNames.ROLES, "USER,ADMIN");
        request.addHeader(SanguiIdentityHeaderNames.PERMISSIONS, "order:create");
        request.addHeader(SanguiIdentityHeaderNames.JWT_ID, "jwt-001");
        return request;
    }

}
