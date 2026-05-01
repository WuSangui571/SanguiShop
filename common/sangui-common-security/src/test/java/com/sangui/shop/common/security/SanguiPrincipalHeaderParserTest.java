package com.sangui.shop.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SanguiPrincipalHeaderParserTest {

    @Test
    void fullTrustedHeadersParsePrincipal() {
        Map<String, String> headers = headers();

        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(headers::get);

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo("10001");
        assertThat(principal.get().shopId()).isEqualTo(1L);
        assertThat(principal.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(principal.get().permissions()).containsExactly("order:create");
        assertThat(principal.get().jwtId()).isEqualTo("jwt-001");
    }

    @Test
    void missingUserIdReturnsEmpty() {
        Map<String, String> headers = headers();
        headers.remove(SanguiIdentityHeaderNames.USER_ID);

        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(headers::get);

        assertThat(principal).isEmpty();
    }

    @Test
    void missingShopIdReturnsEmpty() {
        Map<String, String> headers = headers();
        headers.remove(SanguiIdentityHeaderNames.SHOP_ID);

        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(headers::get);

        assertThat(principal).isEmpty();
    }

    @Test
    void invalidShopIdReturnsEmpty() {
        Map<String, String> headers = headers();
        headers.put(SanguiIdentityHeaderNames.SHOP_ID, "not-a-number");

        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(headers::get);

        assertThat(principal).isEmpty();
    }

    @Test
    void blankRoleAndPermissionSegmentsAreIgnored() {
        Map<String, String> headers = headers();
        headers.put(SanguiIdentityHeaderNames.ROLES, " USER, ,ADMIN ");
        headers.put(SanguiIdentityHeaderNames.PERMISSIONS, " ,order:create, ");

        Optional<SanguiPrincipal> principal = SanguiPrincipalHeaderParser.parse(headers::get);

        assertThat(principal).isPresent();
        assertThat(principal.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(principal.get().permissions()).containsExactly("order:create");
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put(SanguiIdentityHeaderNames.USER_ID, "10001");
        headers.put(SanguiIdentityHeaderNames.SHOP_ID, "1");
        headers.put(SanguiIdentityHeaderNames.ROLES, "USER,ADMIN");
        headers.put(SanguiIdentityHeaderNames.PERMISSIONS, "order:create");
        headers.put(SanguiIdentityHeaderNames.JWT_ID, "jwt-001");
        return headers;
    }
}
