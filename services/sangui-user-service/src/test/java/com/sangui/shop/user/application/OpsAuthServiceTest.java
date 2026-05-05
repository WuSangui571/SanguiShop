package com.sangui.shop.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.OpsSessionResponse;
import com.sangui.shop.user.domain.UserAccount;
import com.sangui.shop.user.domain.UserRepository;
import com.sangui.shop.user.infrastructure.crypto.BCryptPasswordHasher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsAuthServiceTest {

    private InMemoryUserRepository userRepository;
    private BCryptPasswordHasher passwordHasher;
    private CapturingTokenIssuer tokenIssuer;
    private OpsAdminIdentityRegistry adminIdentityRegistry;
    private OpsAuthService opsAuthService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordHasher = new BCryptPasswordHasher();
        tokenIssuer = new CapturingTokenIssuer();
        adminIdentityRegistry = new OpsAdminIdentityRegistry();
        opsAuthService = new OpsAuthService(userRepository, passwordHasher, tokenIssuer, adminIdentityRegistry);
    }

    @Test
    void loginIssuesAdminTokenForConfiguredOpsUser() {
        adminIdentityRegistry.setAdmins(List.of(adminIdentity(1L, "ops-admin")));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.login(new LoginUserRequest(1L, "ops-admin", "Passw0rd!"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("ops-admin");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(response.permissions()).isEmpty();
        assertThat(tokenIssuer.lastRoles).containsExactly("ADMIN");
        assertThat(userRepository.loginSuccessIds).containsExactly(userId);
    }

    @Test
    void loginRejectsAuthenticatedUserWithoutOpsAccess() {
        userRepository.save(1L, "alice", "13800000000", passwordHasher.hash("Passw0rd!"));

        assertThatThrownBy(() -> opsAuthService.login(new LoginUserRequest(1L, "alice", "Passw0rd!")))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void refreshReissuesTokenForConfiguredOpsPrincipal() {
        adminIdentityRegistry.setAdmins(List.of(adminIdentity(1L, "ops-admin")));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.refresh(new SanguiPrincipal(
                String.valueOf(userId),
                1L,
                java.util.Set.of("ADMIN"),
                java.util.Set.of(),
                "jwt-ops-1"
        ));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(tokenIssuer.lastRoles).containsExactly("ADMIN");
    }

    @Test
    void refreshRejectsShopMismatch() {
        adminIdentityRegistry.setAdmins(List.of(adminIdentity(1L, "ops-admin")));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        assertThatThrownBy(() -> opsAuthService.refresh(new SanguiPrincipal(
                String.valueOf(userId),
                2L,
                java.util.Set.of("ADMIN"),
                java.util.Set.of(),
                "jwt-ops-2"
        )))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    private OpsAdminIdentityRegistry.AdminIdentity adminIdentity(Long shopId, String username) {
        OpsAdminIdentityRegistry.AdminIdentity identity = new OpsAdminIdentityRegistry.AdminIdentity();
        identity.setShopId(shopId);
        identity.setUsername(username);
        return identity;
    }

    private static final class CapturingTokenIssuer implements UserTokenIssuer {

        private List<String> lastRoles = List.of();

        @Override
        public TokenInfo issue(Long userId, Long shopId, List<String> roles, List<String> permissions) {
            lastRoles = List.copyOf(roles);
            return new TokenInfo("jwt-admin-token", 3600);
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final AtomicLong nextId = new AtomicLong(20000);
        private final Map<Long, UserAccount> usersById = new LinkedHashMap<>();
        private final List<Long> loginSuccessIds = new ArrayList<>();

        @Override
        public Optional<UserAccount> findById(Long userId) {
            return Optional.ofNullable(usersById.get(userId));
        }

        @Override
        public Optional<UserAccount> findByUsername(Long shopId, String username) {
            return usersById.values().stream()
                    .filter(user -> user.shopId().equals(shopId))
                    .filter(user -> user.username().equals(username))
                    .findFirst();
        }

        @Override
        public Optional<UserAccount> findByMobile(Long shopId, String mobile) {
            return usersById.values().stream()
                    .filter(user -> user.shopId().equals(shopId))
                    .filter(user -> user.mobile().equals(mobile))
                    .findFirst();
        }

        @Override
        public Long save(Long shopId, String username, String mobile, String passwordHash) {
            Long id = nextId.incrementAndGet();
            usersById.put(id, new UserAccount(id, shopId, username, mobile, passwordHash, "ACTIVE"));
            return id;
        }

        @Override
        public void markLoginSuccess(Long userId) {
            loginSuccessIds.add(userId);
        }
    }
}
