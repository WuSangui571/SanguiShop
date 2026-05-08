package com.sangui.shop.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.JwtClaimConstants;
import com.sangui.shop.common.security.SanguiIdentityHeaderNames;
import com.sangui.shop.gateway.config.GatewayJwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayJwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayJwtAuthenticationFilter.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> TRUSTED_IDENTITY_HEADERS = Set.of(
            SanguiIdentityHeaderNames.USER_ID,
            SanguiIdentityHeaderNames.SHOP_ID,
            SanguiIdentityHeaderNames.ROLES,
            SanguiIdentityHeaderNames.PERMISSIONS,
            SanguiIdentityHeaderNames.JWT_ID
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String secret;
    private final String issuer;
    private final long allowedClockSkewSeconds;

    public GatewayJwtAuthenticationFilter(
            ObjectMapper objectMapper,
            GatewayJwtProperties properties,
            Clock gatewayClock
    ) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException(CommonErrorCode.CONFIG_SECRET_MISSING.code());
        }
        if (properties.getIssuer() == null || properties.getIssuer().isBlank()) {
            throw new IllegalStateException(CommonErrorCode.CONFIG_SECRET_MISSING.code());
        }
        this.objectMapper = objectMapper;
        this.clock = gatewayClock;
        this.secret = properties.getSecret();
        this.issuer = properties.getIssuer();
        this.allowedClockSkewSeconds = properties.getAllowedClockSkewSeconds();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = traceId(exchange);
        ServerWebExchange sanitizedExchange = sanitizeTrustedHeaders(exchange, traceId);

        if (!isApiRequest(sanitizedExchange) || isCorsPreflightRequest(sanitizedExchange) || isPublicEndpoint(sanitizedExchange)) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return unauthorized(sanitizedExchange, CommonErrorCode.AUTH_TOKEN_MISSING, traceId);
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            return unauthorized(sanitizedExchange, CommonErrorCode.SIGNATURE_INVALID, traceId);
        }

        try {
            GatewayJwtPrincipal principal = validate(authorization.substring(BEARER_PREFIX.length()));
            ServerWebExchange authenticatedExchange = withTrustedHeaders(sanitizedExchange, principal, traceId);
            return chain.filter(authenticatedExchange);
        } catch (GatewayJwtException exception) {
            log.warn(
                    "Gateway JWT authentication failed path={} code={} traceId={}",
                    sanitizedExchange.getRequest().getPath().value(),
                    exception.errorCode().code(),
                    traceId
            );
            return unauthorized(sanitizedExchange, exception.errorCode(), traceId);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private boolean isApiRequest(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value().startsWith("/api/");
    }

    private boolean isPublicEndpoint(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        return isPublicAuthEndpoint(request, path)
                || isPublicProductReadEndpoint(request, path)
                || isPublicUploadReadEndpoint(request, path);
    }

    private boolean isPublicAuthEndpoint(ServerHttpRequest request, String path) {
        return request.getMethod() == HttpMethod.POST
                && ("/api/users/register".equals(path)
                        || "/api/users/login".equals(path)
                        || "/api/users/ops/login".equals(path));
    }

    private boolean isPublicProductReadEndpoint(ServerHttpRequest request, String path) {
        return request.getMethod() == HttpMethod.GET
                && ("/api/products".equals(path) || path.matches("^/api/products/\\d+(/reviews)?$"));
    }

    private boolean isPublicUploadReadEndpoint(ServerHttpRequest request, String path) {
        return request.getMethod() == HttpMethod.GET
                && path.matches("^/api/uploads/review-images/[A-Za-z0-9._-]+\\.(jpg|jpeg|png|webp)$");
    }

    private boolean isCorsPreflightRequest(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        return request.getMethod() == HttpMethod.OPTIONS
                && request.getHeaders().containsKey(HttpHeaders.ORIGIN)
                && request.getHeaders().containsKey(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
    }

    private ServerWebExchange sanitizeTrustedHeaders(ServerWebExchange exchange, String traceId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    TRUSTED_IDENTITY_HEADERS.forEach(headers::remove);
                    headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                })
                .build();
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withTrustedHeaders(
            ServerWebExchange exchange,
            GatewayJwtPrincipal principal,
            String traceId
    ) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(SanguiIdentityHeaderNames.USER_ID, principal.userId());
                    headers.set(SanguiIdentityHeaderNames.SHOP_ID, String.valueOf(principal.shopId()));
                    headers.set(SanguiIdentityHeaderNames.ROLES, String.join(",", principal.roles()));
                    headers.set(SanguiIdentityHeaderNames.PERMISSIONS, String.join(",", principal.permissions()));
                    headers.set(SanguiIdentityHeaderNames.JWT_ID, principal.jwtId());
                    headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            CommonErrorCode errorCode,
            String traceId
    ) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResult.failure(
                    errorCode.code(),
                    errorCode.defaultMessage(),
                    traceId
            ));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private GatewayJwtPrincipal validate(String token) {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }

        JsonNode header = decodeJson(segments[0]);
        JsonNode payload = decodeJson(segments[1]);
        if (!"HS256".equals(text(header, "alg")) || !"JWT".equals(text(header, "typ"))) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
        if (!MessageDigest.isEqual(sign(segments[0] + "." + segments[1]).getBytes(StandardCharsets.UTF_8),
                segments[2].getBytes(StandardCharsets.UTF_8))) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }

        String userId = requiredText(payload, JwtClaimConstants.SUBJECT);
        String tokenIssuer = requiredText(payload, JwtClaimConstants.ISSUER);
        if (!issuer.equals(tokenIssuer)) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }

        long issuedAt = requiredLong(payload, JwtClaimConstants.ISSUED_AT);
        long expiresAt = requiredLong(payload, JwtClaimConstants.EXPIRES_AT);
        validateTimes(issuedAt, expiresAt);

        return new GatewayJwtPrincipal(
                userId,
                requiredLong(payload, JwtClaimConstants.SHOP_ID),
                requiredTextArray(payload, JwtClaimConstants.ROLES),
                requiredTextArray(payload, JwtClaimConstants.PERMISSIONS),
                requiredText(payload, JwtClaimConstants.JWT_ID)
        );
    }

    private JsonNode decodeJson(String segment) {
        try {
            return objectMapper.readTree(Base64.getUrlDecoder().decode(segment));
        } catch (Exception exception) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
    }

    private String requiredText(JsonNode payload, String claimName) {
        String value = text(payload, claimName);
        if (value == null || value.isBlank()) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
        return value;
    }

    private String text(JsonNode payload, String claimName) {
        JsonNode value = payload.get(claimName);
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String claimName) {
        JsonNode value = payload.get(claimName);
        if (value == null) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
        if (value.isIntegralNumber() && value.canConvertToLong()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText());
            } catch (NumberFormatException exception) {
                throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
            }
        }
        throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
    }

    private List<String> requiredTextArray(JsonNode payload, String claimName) {
        JsonNode value = payload.get(claimName);
        if (value == null || !value.isArray()) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
        List<String> result = new ArrayList<>();
        value.forEach(element -> {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
            }
            result.add(element.asText());
        });
        return List.copyOf(result);
    }

    private void validateTimes(long issuedAt, long expiresAt) {
        try {
            Instant now = Instant.now(clock);
            if (now.isAfter(Instant.ofEpochSecond(expiresAt).plusSeconds(allowedClockSkewSeconds))) {
                throw new GatewayJwtException(CommonErrorCode.AUTH_TOKEN_EXPIRED);
            }
            if (Instant.ofEpochSecond(issuedAt).isAfter(now.plusSeconds(allowedClockSkewSeconds))) {
                throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
            }
            if (issuedAt >= expiresAt) {
                throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
            }
        } catch (GatewayJwtException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayJwtException(CommonErrorCode.SIGNATURE_INVALID);
        }
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }

    private record GatewayJwtPrincipal(
            String userId,
            Long shopId,
            List<String> roles,
            List<String> permissions,
            String jwtId
    ) {
    }

    private static class GatewayJwtException extends RuntimeException {

        private final CommonErrorCode errorCode;

        GatewayJwtException(CommonErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        CommonErrorCode errorCode() {
            return errorCode;
        }
    }
}
