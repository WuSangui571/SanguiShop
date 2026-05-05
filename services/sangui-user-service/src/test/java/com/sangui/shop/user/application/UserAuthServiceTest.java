package com.sangui.shop.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.LoginUserResponse;
import com.sangui.shop.user.api.dto.RegisterUserRequest;
import com.sangui.shop.user.api.dto.RegisterUserResponse;
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

class UserAuthServiceTest {

    private InMemoryUserRepository userRepository;
    private BCryptPasswordHasher passwordHasher;
    private CapturingTokenIssuer tokenIssuer;
    private UserAuthService userAuthService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordHasher = new BCryptPasswordHasher();
        tokenIssuer = new CapturingTokenIssuer();
        userAuthService = new UserAuthService(userRepository, passwordHasher, tokenIssuer);
    }

    @Test
    void registerHashesPasswordAndReturnsPublicUserData() {
        RegisterUserResponse response = userAuthService.register(new RegisterUserRequest(
                1L,
                "alice",
                "13800000000",
                "Passw0rd!"
        ));

        UserAccount savedUser = userRepository.findByUsername(1L, "alice").orElseThrow();
        assertThat(response.userId()).isEqualTo(savedUser.id());
        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.roles()).containsExactly("USER");
        assertThat(savedUser.passwordHash()).isNotEqualTo("Passw0rd!");
        assertThat(passwordHasher.matches("Passw0rd!", savedUser.passwordHash())).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsernameWithinShop() {
        userAuthService.register(new RegisterUserRequest(1L, "alice", "13800000000", "Passw0rd!"));

        assertThatThrownBy(() -> userAuthService.register(new RegisterUserRequest(
                1L,
                "alice",
                "13900000000",
                "Passw0rd!"
        )))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("USER_USERNAME_EXISTS");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void registerRejectsDuplicateMobileWithinShop() {
        userAuthService.register(new RegisterUserRequest(1L, "alice", "13800000000", "Passw0rd!"));

        assertThatThrownBy(() -> userAuthService.register(new RegisterUserRequest(
                1L,
                "bob",
                "13800000000",
                "Passw0rd!"
        )))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("USER_MOBILE_EXISTS");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void loginAuthenticatesUsernameAndIssuesToken() {
        RegisterUserResponse registered = userAuthService.register(new RegisterUserRequest(
                1L,
                "alice",
                "13800000000",
                "Passw0rd!"
        ));

        LoginUserResponse response = userAuthService.login(new LoginUserRequest(1L, "alice", "Passw0rd!"));

        assertThat(response.userId()).isEqualTo(registered.userId());
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.roles()).containsExactly("USER");
        assertThat(userRepository.loginSuccessIds).containsExactly(registered.userId());
        assertThat(tokenIssuer.lastUserId).isEqualTo(registered.userId());
        assertThat(tokenIssuer.lastShopId).isEqualTo(1L);
        assertThat(tokenIssuer.lastRoles).containsExactly("USER");
    }

    @Test
    void loginAuthenticatesMobileWithinShop() {
        RegisterUserResponse registered = userAuthService.register(new RegisterUserRequest(
                1L,
                "alice",
                "13800000000",
                "Passw0rd!"
        ));

        LoginUserResponse response = userAuthService.login(new LoginUserRequest(1L, "13800000000", "Passw0rd!"));

        assertThat(response.userId()).isEqualTo(registered.userId());
    }

    @Test
    void loginRejectsWrongPasswordWithGenericCredentialError() {
        userAuthService.register(new RegisterUserRequest(1L, "alice", "13800000000", "Passw0rd!"));

        assertThatThrownBy(() -> userAuthService.login(new LoginUserRequest(1L, "alice", "wrong-password")))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                    assertThat(exception.httpStatus()).isEqualTo(401);
                });
    }

    @Test
    void loginRejectsUnknownIdentityWithGenericCredentialError() {
        assertThatThrownBy(() -> userAuthService.login(new LoginUserRequest(1L, "missing", "Passw0rd!")))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                    assertThat(exception.httpStatus()).isEqualTo(401);
                });
    }

    private static final class CapturingTokenIssuer implements UserTokenIssuer {

        private Long lastUserId;
        private Long lastShopId;
        private List<String> lastRoles = List.of();

        @Override
        public TokenInfo issue(Long userId, Long shopId, List<String> roles, List<String> permissions) {
            lastUserId = userId;
            lastShopId = shopId;
            lastRoles = List.copyOf(roles);
            return new TokenInfo("jwt-token", 7200);
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final AtomicLong nextId = new AtomicLong(10000);
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
