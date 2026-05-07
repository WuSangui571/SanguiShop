package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderShipmentMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V6__add_order_shipment_snapshot.sql";

    @Test
    void orderShipmentMigrationAddsFulfillmentSnapshotColumns() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("fulfillment_status");
        assertThat(sql).contains("carrier");
        assertThat(sql).contains("tracking_no");
        assertThat(sql).contains("shipped_at");
        assertThat(sql).contains("shipment_request_id");
        assertThat(sql).contains("idx_oms_order_shop_fulfillment_created");
    }
}
