package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderReviewVisibilityMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V9__add_order_review_visibility_moderation.sql";

    @Test
    void reviewVisibilityMigrationAddsModerationSnapshotColumnsAndIndex() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("alter table oms_order_review");
        assertThat(sql).contains("visibility_status varchar(16) not null default 'visible'");
        assertThat(sql).contains("visibility_reason varchar(200)");
        assertThat(sql).contains("visibility_request_id varchar(64)");
        assertThat(sql).contains("visibility_operator varchar(64)");
        assertThat(sql).contains("visibility_trace_id varchar(64)");
        assertThat(sql).contains("visibility_updated_at datetime");
        assertThat(sql).contains("idx_oms_order_review_shop_visibility_created");
    }

    @Test
    void publicProductReviewSqlFiltersHiddenReviews() throws IOException {
        URL source = Thread.currentThread().getContextClassLoader()
                .getResource("com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.class");

        assertThat(source).isNotNull();

        String javaSource = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.java"),
                StandardCharsets.UTF_8
        ).toLowerCase(Locale.ROOT);
        assertThat(javaSource).contains("coalesce(r.visibility_status, 'visible') = 'visible'");
    }
}
