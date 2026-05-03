package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderCompensationAttemptHistoryMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V5__add_order_compensation_attempt_history.sql";

    @Test
    void orderCompensationAttemptHistoryMigrationAddsOperatorAndHistoryTable() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("last_compensation_operator");
        assertThat(sql).contains("create table oms_order_compensation_attempt");
        assertThat(sql).contains("trigger_type");
        assertThat(sql).contains("operator");
        assertThat(sql).contains("idx_oms_order_comp_attempt_shop_order_created");
    }
}
