package com.sangui.shop.user.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.OpsSessionResponse;
import com.sangui.shop.user.domain.UserAccount;
import com.sangui.shop.user.domain.UserErrorCode;
import com.sangui.shop.user.domain.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsAuthService {

    private static final List<String> OPS_SESSION_ROLES = List.of();
    private static final List<String> ADMIN_SESSION_PERMISSIONS = List.of(
            SanguiPermissionConstants.OPS_COMPENSATION_ADMIN,
            SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN,
            SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN,
            SanguiPermissionConstants.REVIEW_MANAGEMENT_ADMIN,
            SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN,
            SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN
    );

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserTokenIssuer tokenIssuer;
    private final OpsAccessRegistry opsAccessRegistry;

    public OpsAuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            UserTokenIssuer tokenIssuer,
            OpsAccessRegistry opsAccessRegistry
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.opsAccessRegistry = opsAccessRegistry;
    }

    @Transactional
    public OpsSessionResponse login(LoginUserRequest request) {
        UserAccount user = userRepository.findByUsernameOrMobile(request.shopId(), request.usernameOrMobile())
                .filter(account -> passwordHasher.matches(request.password(), account.passwordHash()))
                .orElseThrow(() -> new SanguiException(UserErrorCode.AUTH_INVALID_CREDENTIALS, 401));

        OpsAccessRegistry.ResolvedOpsAccess access = requireCompensationOpsAccess(user.shopId(), user.username());
        userRepository.markLoginSuccess(user.id());
        return issueSession(user, access);
    }

    public OpsSessionResponse refresh(SanguiPrincipal principal) {
        UserAccount user = userRepository.findById(parsePrincipalUserId(principal))
                .orElseThrow(() -> new SanguiException(CommonErrorCode.AUTH_TOKEN_MISSING, 401));
        if (!user.shopId().equals(principal.shopId())) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
        OpsAccessRegistry.ResolvedOpsAccess access = requireCompensationOpsAccess(user.shopId(), user.username());
        return issueSession(user, access);
    }

    private OpsSessionResponse issueSession(UserAccount user, OpsAccessRegistry.ResolvedOpsAccess access) {
        List<String> permissions = access.permissions();
        TokenInfo token = tokenIssuer.issue(user.id(), user.shopId(), OPS_SESSION_ROLES, permissions);
        return new OpsSessionResponse(
                user.id(),
                user.shopId(),
                user.username(),
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                OPS_SESSION_ROLES,
                permissions
        );
    }

    private OpsAccessRegistry.ResolvedOpsAccess requireCompensationOpsAccess(Long shopId, String username) {
        OpsAccessRegistry.ResolvedOpsAccess access = opsAccessRegistry.resolve(shopId, username)
                .orElseThrow(() -> new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403));
        if (access.permissions().stream().noneMatch(ADMIN_SESSION_PERMISSIONS::contains)) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
        return access;
    }

    private Long parsePrincipalUserId(SanguiPrincipal principal) {
        try {
            return Long.parseLong(principal.userId());
        } catch (RuntimeException exception) {
            throw new SanguiException(CommonErrorCode.AUTH_TOKEN_MISSING, 401);
        }
    }
}
