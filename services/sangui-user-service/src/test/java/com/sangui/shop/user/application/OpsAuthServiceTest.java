package com.sangui.shop.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
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
    private OpsAccessRegistry accessRegistry;
    private OpsAuthService opsAuthService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordHasher = new BCryptPasswordHasher();
        tokenIssuer = new CapturingTokenIssuer();
        accessRegistry = new OpsAccessRegistry();
        opsAuthService = new OpsAuthService(userRepository, passwordHasher, tokenIssuer, accessRegistry);
    }

    @Test
    void loginIssuesCompensationPermissionForConfiguredOpsBinding() {
        accessRegistry.setBindings(List.of(accessBinding(1L, "ops-admin", SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.login(new LoginUserRequest(1L, "ops-admin", "Passw0rd!"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("ops-admin");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.roles()).isEmpty();
        assertThat(response.permissions()).containsExactly(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
        assertThat(tokenIssuer.lastRoles).isEmpty();
        assertThat(tokenIssuer.lastPermissions).containsExactly(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
        assertThat(userRepository.loginSuccessIds).containsExactly(userId);
    }

    @Test
    void loginFallsBackToLegacyAdminsConfig() {
        accessRegistry.setAdmins(List.of(adminIdentity(1L, "ops-admin")));
        userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.login(new LoginUserRequest(1L, "ops-admin", "Passw0rd!"));

        assertThat(response.roles()).isEmpty();
        assertThat(response.permissions()).containsExactly(
                SanguiPermissionConstants.OPS_COMPENSATION_ADMIN,
                SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN,
                SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN
        );
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
        accessRegistry.setBindings(List.of(accessBinding(1L, "ops-admin", SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.refresh(new SanguiPrincipal(
                String.valueOf(userId),
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN),
                "jwt-ops-1"
        ));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.roles()).isEmpty();
        assertThat(response.permissions()).containsExactly(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
        assertThat(tokenIssuer.lastRoles).isEmpty();
        assertThat(tokenIssuer.lastPermissions).containsExactly(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
    }

    @Test
    void refreshRejectsShopMismatch() {
        accessRegistry.setBindings(List.of(accessBinding(1L, "ops-admin", SanguiPermissionConstants.OPS_COMPENSATION_ADMIN)));
        Long userId = userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        assertThatThrownBy(() -> opsAuthService.refresh(new SanguiPrincipal(
                String.valueOf(userId),
                2L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN),
                "jwt-ops-2"
        )))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void loginRejectsBindingWithoutCompensationPermission() {
        accessRegistry.setBindings(List.of(accessBinding(1L, "ops-admin", "OPS_OTHER_PERMISSION")));
        userRepository.save(1L, "ops-admin", "13800000000", passwordHasher.hash("Passw0rd!"));

        assertThatThrownBy(() -> opsAuthService.login(new LoginUserRequest(1L, "ops-admin", "Passw0rd!")))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void loginAllowsProductCatalogAdminPermission() {
        accessRegistry.setBindings(List.of(accessBinding(1L, "product-admin", SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN)));
        userRepository.save(1L, "product-admin", "13800000001", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.login(new LoginUserRequest(1L, "product-admin", "Passw0rd!"));

        assertThat(response.permissions()).containsExactly(SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN);
        assertThat(tokenIssuer.lastPermissions).containsExactly(SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN);
    }

    @Test
    void loginAllowsOrderManagementAdminPermission() {
        accessRegistry.setBindings(List.of(accessBinding(1L, "order-admin", SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN)));
        userRepository.save(1L, "order-admin", "13800000002", passwordHasher.hash("Passw0rd!"));

        OpsSessionResponse response = opsAuthService.login(new LoginUserRequest(1L, "order-admin", "Passw0rd!"));

        assertThat(response.permissions()).containsExactly(SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN);
        assertThat(tokenIssuer.lastPermissions).containsExactly(SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN);
    }

    private OpsAccessRegistry.OpsAccessBinding accessBinding(Long shopId, String username, String... permissions) {
        OpsAccessRegistry.OpsAccessBinding binding = new OpsAccessRegistry.OpsAccessBinding();
        binding.setShopId(shopId);
        binding.setUsername(username);
        binding.setPermissions(List.of(permissions));
        return binding;
    }

    private OpsAccessRegistry.AdminIdentity adminIdentity(Long shopId, String username) {
        OpsAccessRegistry.AdminIdentity identity = new OpsAccessRegistry.AdminIdentity();
        identity.setShopId(shopId);
        identity.setUsername(username);
        return identity;
    }

    private static final class CapturingTokenIssuer implements UserTokenIssuer {

        private List<String> lastRoles = List.of();
        private List<String> lastPermissions = List.of();

        @Override
        public TokenInfo issue(Long userId, Long shopId, List<String> roles, List<String> permissions) {
            lastRoles = List.copyOf(roles);
            lastPermissions = List.copyOf(permissions);
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
