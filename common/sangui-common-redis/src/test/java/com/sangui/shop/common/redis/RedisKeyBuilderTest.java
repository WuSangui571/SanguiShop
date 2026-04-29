package com.sangui.shop.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisKeyBuilderTest {

    @Test
    void buildsCanonicalSanguiRedisKey() {
        String key = RedisKeyBuilder.key("prod", "seckill", "stock", "activity-100:sku-200");

        assertThat(key).isEqualTo("sangui:prod:seckill:stock:activity-100:sku-200");
    }
}
