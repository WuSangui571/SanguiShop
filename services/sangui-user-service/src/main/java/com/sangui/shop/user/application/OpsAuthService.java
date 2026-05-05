package com.sangui.shop.user.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
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

    private static final List<String> OPS_ADMIN_ROLES = List.of("ADMIN");
    private static final List<String> OPS_ADMIN_PERMISSIONS = List.of();

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserTokenIssuer tokenIssuer;
    private final OpsAdminIdentityRegistry opsAdminIdentityRegistry;

    public OpsAuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            UserTokenIssuer tokenIssuer,
            OpsAdminIdentityRegistry opsAdminIdentityRegistry
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.opsAdminIdentityRegistry = opsAdminIdentityRegistry;
    }

    @Transactional
    public OpsSessionResponse login(LoginUserRequest request) {
        UserAccount user = userRepository.findByUsernameOrMobile(request.shopId(), request.usernameOrMobile())
                .filter(account -> passwordHasher.matches(request.password(), account.passwordHash()))
                .orElseThrow(() -> new SanguiException(UserErrorCode.AUTH_INVALID_CREDENTIALS, 401));

        requireOpsAdmin(user.shopId(), user.username());
        userRepository.markLoginSuccess(user.id());
        return issueSession(user);
    }

    public OpsSessionResponse refresh(SanguiPrincipal principal) {
        UserAccount user = userRepository.findById(parsePrincipalUserId(principal))
                .orElseThrow(() -> new SanguiException(CommonErrorCode.AUTH_TOKEN_MISSING, 401));
        if (!user.shopId().equals(principal.shopId())) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
        requireOpsAdmin(user.shopId(), user.username());
        return issueSession(user);
    }

    private OpsSessionResponse issueSession(UserAccount user) {
        TokenInfo token = tokenIssuer.issue(user.id(), user.shopId(), OPS_ADMIN_ROLES, OPS_ADMIN_PERMISSIONS);
        return new OpsSessionResponse(
                user.id(),
                user.shopId(),
                user.username(),
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                OPS_ADMIN_ROLES,
                OPS_ADMIN_PERMISSIONS
        );
    }

    private void requireOpsAdmin(Long shopId, String username) {
        if (!opsAdminIdentityRegistry.isOpsAdmin(shopId, username)) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private Long parsePrincipalUserId(SanguiPrincipal principal) {
        try {
            return Long.parseLong(principal.userId());
        } catch (RuntimeException exception) {
            throw new SanguiException(CommonErrorCode.AUTH_TOKEN_MISSING, 401);
        }
    }
}
