package com.sangui.shop.common.web;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.security.SanguiSecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpsAuditLogger {

    public static final String REQUEST_LOGGED_ATTRIBUTE = OpsAuditLogger.class.getName() + ".logged";

    private static final Logger log = LoggerFactory.getLogger(OpsAuditLogger.class);

    private OpsAuditLogger() {
    }

    public static EventBuilder event(HttpServletRequest request, String action) {
        return new EventBuilder()
                .action(action)
                .traceId(traceId(request))
                .method(request.getMethod())
                .path(request.getRequestURI())
                .ip(clientIp(request))
                .fromPrincipal(principal(request));
    }

    public static void log(HttpServletRequest request, OpsAuditEvent event) {
        request.setAttribute(REQUEST_LOGGED_ATTRIBUTE, Boolean.TRUE);
        log.info(
                "Ops audit event. action={} outcome={} result={} traceId={} method={} path={} shopId={} userId={} username={} userIdentifier={} operator={} permission={} targetType={} targetId={} targetCount={} dryRun={} errorCode={} reason={} ip={} jwtId={}",
                normalized(event.action()),
                normalized(event.outcome()),
                normalized(event.result()),
                normalized(event.traceId()),
                normalized(event.method()),
                normalized(event.path()),
                event.shopId(),
                normalized(event.userId()),
                normalized(event.username()),
                normalized(event.userIdentifier()),
                normalized(event.operator()),
                normalized(event.permission()),
                normalized(event.targetType()),
                normalized(event.targetId()),
                event.targetCount(),
                event.dryRun(),
                normalized(event.errorCode()),
                normalized(event.reason()),
                normalized(event.ip()),
                normalized(event.jwtId())
        );
    }

    public static boolean isLogged(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(REQUEST_LOGGED_ATTRIBUTE));
    }

    public static String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }

    public static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] segments = forwardedFor.split(",");
            return sanitize(segments[0]);
        }
        return sanitize(request.getRemoteAddr());
    }

    public static String outcome(Throwable exception) {
        if (exception instanceof SanguiException sanguiException && sanguiException.httpStatus() == 403) {
            return "denied";
        }
        return "failed";
    }

    public static String errorCode(Throwable exception) {
        if (exception instanceof SanguiException sanguiException) {
            return sanguiException.errorCode().code();
        }
        return CommonErrorCode.INTERNAL_ERROR.code();
    }

    public static String reason(Throwable exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        return sanitize(message.replaceAll("[\\r\\n]+", " "));
    }

    public static SanguiPrincipal principal(HttpServletRequest request) {
        Object attribute = request.getAttribute(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE);
        if (attribute instanceof SanguiPrincipal principal) {
            return principal;
        }
        Optional<SanguiPrincipal> current = SanguiSecurityContext.currentPrincipal();
        return current.orElse(null);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalized(String value) {
        String sanitized = sanitize(value);
        return sanitized == null ? "-" : sanitized;
    }

    public record OpsAuditEvent(
            String action,
            String outcome,
            String result,
            String traceId,
            String method,
            String path,
            Long shopId,
            String userId,
            String username,
            String userIdentifier,
            String operator,
            String permission,
            String targetType,
            String targetId,
            Integer targetCount,
            Boolean dryRun,
            String errorCode,
            String reason,
            String ip,
            String jwtId
    ) {
    }

    public static final class EventBuilder {

        private String action;
        private String outcome;
        private String result;
        private String traceId;
        private String method;
        private String path;
        private Long shopId;
        private String userId;
        private String username;
        private String userIdentifier;
        private String operator;
        private String permission;
        private String targetType;
        private String targetId;
        private Integer targetCount;
        private Boolean dryRun;
        private String errorCode;
        private String reason;
        private String ip;
        private String jwtId;

        public EventBuilder action(String action) {
            this.action = action;
            return this;
        }

        public EventBuilder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public EventBuilder result(String result) {
            this.result = result;
            return this;
        }

        public EventBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public EventBuilder method(String method) {
            this.method = method;
            return this;
        }

        public EventBuilder path(String path) {
            this.path = path;
            return this;
        }

        public EventBuilder shopId(Long shopId) {
            this.shopId = shopId;
            return this;
        }

        public EventBuilder userId(String userId) {
            this.userId = sanitize(userId);
            return this;
        }

        public EventBuilder username(String username) {
            this.username = sanitize(username);
            return this;
        }

        public EventBuilder userIdentifier(String userIdentifier) {
            this.userIdentifier = sanitize(userIdentifier);
            return this;
        }

        public EventBuilder operator(String operator) {
            this.operator = sanitize(operator);
            return this;
        }

        public EventBuilder permission(String permission) {
            this.permission = sanitize(permission);
            return this;
        }

        public EventBuilder targetType(String targetType) {
            this.targetType = sanitize(targetType);
            return this;
        }

        public EventBuilder targetId(String targetId) {
            this.targetId = sanitize(targetId);
            return this;
        }

        public EventBuilder targetCount(Integer targetCount) {
            this.targetCount = targetCount;
            return this;
        }

        public EventBuilder dryRun(Boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public EventBuilder errorCode(String errorCode) {
            this.errorCode = sanitize(errorCode);
            return this;
        }

        public EventBuilder reason(String reason) {
            this.reason = sanitize(reason);
            return this;
        }

        public EventBuilder ip(String ip) {
            this.ip = sanitize(ip);
            return this;
        }

        public EventBuilder jwtId(String jwtId) {
            this.jwtId = sanitize(jwtId);
            return this;
        }

        public EventBuilder fromPrincipal(SanguiPrincipal principal) {
            if (principal == null) {
                return this;
            }
            this.userId = sanitize(principal.userId());
            this.shopId = principal.shopId();
            this.jwtId = sanitize(principal.jwtId());
            return this;
        }

        public OpsAuditEvent build() {
            return new OpsAuditEvent(
                    action,
                    outcome,
                    result,
                    traceId,
                    method,
                    path,
                    shopId,
                    userId,
                    username,
                    userIdentifier,
                    operator,
                    permission,
                    targetType,
                    targetId,
                    targetCount,
                    dryRun,
                    errorCode,
                    reason,
                    ip,
                    jwtId
            );
        }
    }
}
