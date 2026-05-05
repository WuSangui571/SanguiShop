package com.sangui.shop.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.JwtClaimConstants;
import com.sangui.shop.common.security.SanguiIdentityHeaderNames;
import com.sangui.shop.gateway.config.GatewayJwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayJwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-with-enough-entropy";
    private static final Instant NOW = Instant.parse("2026-05-01T08:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final GatewayJwtAuthenticationFilter filter = new GatewayJwtAuthenticationFilter(
            objectMapper,
            jwtProperties(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void allowsPublicLoginWithoutTokenAndRemovesSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.POST, "/api/users/login")
                .header(SanguiIdentityHeaderNames.USER_ID, "spoofed")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(SanguiIdentityHeaderNames.USER_ID))
                .isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsPublicOpsLoginWithoutTokenAndRemovesSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.POST, "/api/users/ops/login")
                .header(SanguiIdentityHeaderNames.USER_ID, "spoofed-admin")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(SanguiIdentityHeaderNames.USER_ID))
                .isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsCorsPreflightWithoutJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.OPTIONS, "/api/internal/payments/compensation-records/query")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsProtectedApiRequestWithoutBearerToken() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(TraceConstants.TRACE_ID_HEADER, "trace-001")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo(CommonErrorCode.AUTH_TOKEN_MISSING.code());
        assertThat(body.get("traceId").asText()).isEqualTo("trace-001");
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                        NOW.minusSeconds(7200),
                        NOW.minusSeconds(3600),
                        "sanguishop"
                ))
        );

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo(CommonErrorCode.AUTH_TOKEN_EXPIRED.code());
    }

    @Test
    void forwardsTrustedIdentityHeadersForValidJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        "sanguishop"
                ))
                .header(TraceConstants.TRACE_ID_HEADER, "trace-002")
                .header(SanguiIdentityHeaderNames.USER_ID, "spoofed")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(SanguiIdentityHeaderNames.USER_ID)).isEqualTo("10001");
        assertThat(headers.getFirst(SanguiIdentityHeaderNames.SHOP_ID)).isEqualTo("1");
        assertThat(headers.getFirst(SanguiIdentityHeaderNames.ROLES)).isEqualTo("USER,ADMIN");
        assertThat(headers.getFirst(SanguiIdentityHeaderNames.PERMISSIONS)).isEqualTo("order:create");
        assertThat(headers.getFirst(SanguiIdentityHeaderNames.JWT_ID)).isEqualTo("jwt-001");
        assertThat(headers.getFirst(TraceConstants.TRACE_ID_HEADER)).isEqualTo("trace-002");
    }

    @Test
    void rejectsJwtWithWrongIssuer() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        "other-issuer"
                ))
        );

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo(CommonErrorCode.SIGNATURE_INVALID.code());
    }

    @Test
    void rejectsJwtWithInvalidSignature() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        "sanguishop"
                ) + "tampered")
        );

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo(CommonErrorCode.SIGNATURE_INVALID.code());
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private GatewayJwtProperties jwtProperties() {
        GatewayJwtProperties properties = new GatewayJwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("sanguishop");
        properties.setAllowedClockSkewSeconds(60);
        return properties;
    }

    private String token(Instant issuedAt, Instant expiresAt, String issuer) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(JwtClaimConstants.SUBJECT, "10001");
        payload.put(JwtClaimConstants.ISSUER, issuer);
        payload.put(JwtClaimConstants.SHOP_ID, 1L);
        payload.put(JwtClaimConstants.ROLES, List.of("USER", "ADMIN"));
        payload.put(JwtClaimConstants.PERMISSIONS, List.of("order:create"));
        payload.put(JwtClaimConstants.ISSUED_AT, issuedAt.getEpochSecond());
        payload.put(JwtClaimConstants.EXPIRES_AT, expiresAt.getEpochSecond());
        payload.put(JwtClaimConstants.JWT_ID, "jwt-001");

        String unsignedToken = base64Json(header) + "." + base64Json(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
