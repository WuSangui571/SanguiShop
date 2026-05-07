package com.sangui.shop.logistics.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ShipmentMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_shipment_tables.sql";

    @Test
    void shipmentMigrationCreatesIdempotentShipmentTable() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("create table lgs_shipment");
        assertThat(sql).contains("shop_id");
        assertThat(sql).contains("request_id");
        assertThat(sql).contains("trace_id");
        assertThat(sql).contains("uk_lgs_shipment_shop_order");
        assertThat(sql).contains("uk_lgs_shipment_shop_request");
        assertThat(sql).contains("idx_lgs_shipment_shop_status_created");
    }
}
