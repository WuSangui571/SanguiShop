package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderReceiptConfirmationMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V7__add_order_receipt_confirmation_snapshot.sql";

    @Test
    void orderReceiptConfirmationMigrationAddsCompletionSnapshotColumns() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("receipt_request_id");
        assertThat(sql).contains("receipt_trace_id");
        assertThat(sql).contains("completed_at");
        assertThat(sql).contains("idx_oms_order_shop_completed_created");
    }
}
