package com.sangui.shop.user.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.user.api.dto.LoginUserRequest;
import com.sangui.shop.user.api.dto.LoginUserResponse;
import com.sangui.shop.user.api.dto.RegisterUserRequest;
import com.sangui.shop.user.api.dto.RegisterUserResponse;
import com.sangui.shop.user.domain.UserAccount;
import com.sangui.shop.user.domain.UserErrorCode;
import com.sangui.shop.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {

    private static final List<String> DEFAULT_USER_ROLES = List.of("USER");
    private static final List<String> DEFAULT_PERMISSIONS = List.of();

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserTokenIssuer tokenIssuer;

    public UserAuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            UserTokenIssuer tokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public RegisterUserResponse register(RegisterUserRequest request) {
        rejectDuplicateUsername(request.shopId(), request.username());
        rejectDuplicateMobile(request.shopId(), request.mobile());

        String passwordHash = passwordHasher.hash(request.password());
        Long userId;
        try {
            userId = userRepository.save(request.shopId(), request.username(), request.mobile(), passwordHash);
        } catch (DuplicateKeyException exception) {
            if (userRepository.findByMobile(request.shopId(), request.mobile()).isPresent()) {
                throw new SanguiException(UserErrorCode.USER_MOBILE_EXISTS, 409);
            }
            throw new SanguiException(UserErrorCode.USER_USERNAME_EXISTS, 409);
        }

        return new RegisterUserResponse(
                userId,
                request.shopId(),
                request.username(),
                request.mobile(),
                DEFAULT_USER_ROLES
        );
    }

    @Transactional
    public LoginUserResponse login(LoginUserRequest request) {
        UserAccount user = userRepository.findByUsernameOrMobile(request.shopId(), request.usernameOrMobile())
                .filter(account -> passwordHasher.matches(request.password(), account.passwordHash()))
                .orElseThrow(() -> new SanguiException(UserErrorCode.AUTH_INVALID_CREDENTIALS, 401));

        userRepository.markLoginSuccess(user.id());
        TokenInfo token = tokenIssuer.issue(user.id(), user.shopId(), DEFAULT_USER_ROLES, DEFAULT_PERMISSIONS);

        return new LoginUserResponse(
                user.id(),
                user.shopId(),
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                DEFAULT_USER_ROLES
        );
    }

    private void rejectDuplicateUsername(Long shopId, String username) {
        if (userRepository.findByUsername(shopId, username).isPresent()) {
            throw new SanguiException(UserErrorCode.USER_USERNAME_EXISTS, 409);
        }
    }

    private void rejectDuplicateMobile(Long shopId, String mobile) {
        Optional<UserAccount> existingUser = userRepository.findByMobile(shopId, mobile);
        if (existingUser.isPresent()) {
            throw new SanguiException(UserErrorCode.USER_MOBILE_EXISTS, 409);
        }
    }
}
