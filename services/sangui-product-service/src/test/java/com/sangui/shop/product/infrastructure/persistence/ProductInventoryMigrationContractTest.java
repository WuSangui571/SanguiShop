package com.sangui.shop.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ProductInventoryMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V2__add_inventory_reservation_support.sql";

    @Test
    void inventoryMigrationAddsSkuInventoryColumnsAndReservationTable() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();
        assertThat(migration.toString()).contains(MIGRATION_RESOURCE);

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("alter table pms_sku");
        assertThat(sql).contains("available_stock bigint not null default 0");
        assertThat(sql).contains("reserved_stock bigint not null default 0");
        assertThat(sql).contains("create table pms_inventory_reservation");
        assertThat(sql).contains("reservation_no varchar(64) not null");
        assertThat(sql).contains("uk_pms_inventory_reservation_shop_no_sku");
        assertThat(sql).contains("idx_pms_inventory_reservation_shop_no");
    }
}
