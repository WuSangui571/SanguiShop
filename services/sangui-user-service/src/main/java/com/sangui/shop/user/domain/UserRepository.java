package com.sangui.shop.user.domain;

import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findById(Long userId);

    Optional<UserAccount> findByUsername(Long shopId, String username);

    Optional<UserAccount> findByMobile(Long shopId, String mobile);

    default Optional<UserAccount> findByUsernameOrMobile(Long shopId, String usernameOrMobile) {
        return findByUsername(shopId, usernameOrMobile)
                .or(() -> findByMobile(shopId, usernameOrMobile));
    }

    Long save(Long shopId, String username, String mobile, String passwordHash);

    void markLoginSuccess(Long userId);
}
