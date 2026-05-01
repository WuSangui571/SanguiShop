package com.sangui.shop.user.infrastructure.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.JwtClaimConstants;
import com.sangui.shop.user.application.TokenInfo;
import com.sangui.shop.user.application.UserTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacJwtUserTokenIssuer implements UserTokenIssuer {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String secret;
    private final long ttlSeconds;
    private final String issuer;

    @Autowired
    public HmacJwtUserTokenIssuer(
            ObjectMapper objectMapper,
            @Value("${sangui.security.jwt.secret:}") String secret,
            @Value("${sangui.security.jwt.ttl-seconds:7200}") long ttlSeconds,
            @Value("${sangui.security.jwt.issuer:sanguishop}") String issuer
    ) {
        this(objectMapper, Clock.systemUTC(), secret, ttlSeconds, issuer);
    }

    public HmacJwtUserTokenIssuer(ObjectMapper objectMapper, Clock clock, String secret, long ttlSeconds) {
        this(objectMapper, clock, secret, ttlSeconds, "sanguishop");
    }

    public HmacJwtUserTokenIssuer(
            ObjectMapper objectMapper,
            Clock clock,
            String secret,
            long ttlSeconds,
            String issuer
    ) {
        if (secret == null || secret.isBlank()) {
            throw new SanguiException(CommonErrorCode.CONFIG_SECRET_MISSING, 500);
        }
        if (issuer == null || issuer.isBlank()) {
            throw new SanguiException(CommonErrorCode.CONFIG_SECRET_MISSING, 500);
        }
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    @Override
    public TokenInfo issue(Long userId, Long shopId, List<String> roles, List<String> permissions) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(JwtClaimConstants.SUBJECT, String.valueOf(userId));
        payload.put(JwtClaimConstants.ISSUER, issuer);
        payload.put(JwtClaimConstants.SHOP_ID, shopId);
        payload.put(JwtClaimConstants.ROLES, roles);
        payload.put(JwtClaimConstants.PERMISSIONS, permissions);
        payload.put(JwtClaimConstants.ISSUED_AT, issuedAt.getEpochSecond());
        payload.put(JwtClaimConstants.EXPIRES_AT, expiresAt.getEpochSecond());
        payload.put(JwtClaimConstants.JWT_ID, UUID.randomUUID().toString());

        String unsignedToken = base64UrlJson(header) + "." + base64UrlJson(payload);
        return new TokenInfo(unsignedToken + "." + sign(unsignedToken), ttlSeconds);
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JWT payload", exception);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }
}
