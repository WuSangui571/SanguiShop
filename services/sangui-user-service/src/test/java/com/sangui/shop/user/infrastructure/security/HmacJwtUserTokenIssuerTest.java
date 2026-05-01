package com.sangui.shop.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.JwtClaimConstants;
import com.sangui.shop.user.application.TokenInfo;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class HmacJwtUserTokenIssuerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void issuesJwtWithRequiredSanguiClaims() throws Exception {
        HmacJwtUserTokenIssuer issuer = new HmacJwtUserTokenIssuer(
                objectMapper,
                fixedClock,
                "test-secret-with-enough-entropy",
                7200
        );

        TokenInfo token = issuer.issue(10001L, 1L, List.of("USER"), List.of());
        String[] segments = token.accessToken().split("\\.");

        assertThat(segments).hasSize(3);
        assertThat(token.expiresInSeconds()).isEqualTo(7200);

        JsonNode header = decode(segments[0]);
        JsonNode payload = decode(segments[1]);

        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(payload.get(JwtClaimConstants.SUBJECT).asText()).isEqualTo("10001");
        assertThat(payload.get(JwtClaimConstants.ISSUER).asText()).isEqualTo("sanguishop");
        assertThat(payload.get(JwtClaimConstants.SHOP_ID).asLong()).isEqualTo(1L);
        assertThat(payload.get(JwtClaimConstants.ROLES).get(0).asText()).isEqualTo("USER");
        assertThat(payload.get(JwtClaimConstants.PERMISSIONS).isEmpty()).isTrue();
        assertThat(payload.get(JwtClaimConstants.ISSUED_AT).asLong()).isEqualTo(1777449600L);
        assertThat(payload.get(JwtClaimConstants.EXPIRES_AT).asLong()).isEqualTo(1777456800L);
        assertThat(payload.get(JwtClaimConstants.JWT_ID).asText()).isNotBlank();
    }

    @Test
    void rejectsBlankSecretInsteadOfIssuingUnsignedToken() {
        assertThatThrownBy(() -> new HmacJwtUserTokenIssuer(objectMapper, fixedClock, "", 7200))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("CONFIG_SECRET_MISSING");
                    assertThat(exception.httpStatus()).isEqualTo(500);
                });
    }

    @Test
    void rejectsBlankIssuerInsteadOfIssuingUnverifiableToken() {
        assertThatThrownBy(() -> new HmacJwtUserTokenIssuer(
                objectMapper,
                fixedClock,
                "test-secret-with-enough-entropy",
                7200,
                ""
        ))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("CONFIG_SECRET_MISSING");
                    assertThat(exception.httpStatus()).isEqualTo(500);
                });
    }

    private JsonNode decode(String segment) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(segment);
        return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
    }
}
