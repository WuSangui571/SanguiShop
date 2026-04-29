package com.sangui.shop.common.core.tenant;

public final class ShopConstants {

    public static final long DEFAULT_SHOP_ID = 1L;
    public static final String SHOP_ID_FIELD = "shopId";
    public static final String SHOP_ID_HEADER = "X-Shop-Id";
    public static final String SHOP_ID_CLAIM = "shop_id";
    public static final String DEFAULT_SHOP_ID_PROPERTY = "sangui.shop.default-shop-id";

    private ShopConstants() {
    }
}
