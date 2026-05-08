package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderReviewMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V8__create_order_review_tables.sql";

    @Test
    void orderReviewMigrationCreatesReviewTableWithIdempotencyConstraints() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("create table oms_order_review");
        assertThat(sql).contains("shop_id");
        assertThat(sql).contains("order_id");
        assertThat(sql).contains("rating int not null");
        assertThat(sql).contains("content varchar(500)");
        assertThat(sql).contains("image_urls");
        assertThat(sql).contains("request_id varchar(64) not null");
        assertThat(sql).contains("trace_id");
        assertThat(sql).contains("uk_oms_order_review_shop_order");
        assertThat(sql).contains("uk_oms_order_review_shop_user_request");
        assertThat(sql).contains("idx_oms_order_review_shop_user_created");
    }
}
