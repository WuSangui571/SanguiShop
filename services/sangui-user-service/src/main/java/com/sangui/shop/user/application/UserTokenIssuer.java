package com.sangui.shop.user.application;

import java.util.List;

public interface UserTokenIssuer {

    TokenInfo issue(Long userId, Long shopId, List<String> roles, List<String> permissions);
}
