package com.sangui.shop.common.redis;

public final class RedisKeyBuilder {

    private RedisKeyBuilder() {
    }

    public static String key(String env, String service, String domain, String identifier) {
        return "sangui:%s:%s:%s:%s".formatted(env, service, domain, identifier);
    }
}
