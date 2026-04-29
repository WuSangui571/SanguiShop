package com.sangui.shop.common.security;

public final class JwtClaimConstants {

    public static final String SUBJECT = "sub";
    public static final String SHOP_ID = "shop_id";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String ISSUED_AT = "iat";
    public static final String EXPIRES_AT = "exp";
    public static final String JWT_ID = "jti";

    private JwtClaimConstants() {
    }
}
