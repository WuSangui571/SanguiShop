package com.sangui.shop.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ProductMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_product_catalog_tables.sql";

    @Test
    void productMigrationUsesFlywayNamingAndCreatesCatalogTablesContract() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration)
                .as("Flyway migration must be available from classpath:db/migration")
                .isNotNull();
        assertThat(migration.toString())
                .as("Flyway migration filename must remain stable")
                .contains(MIGRATION_RESOURCE);

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("create table pms_product");
        assertThat(sql).contains("create table pms_sku");
        assertThat(sql).contains("shop_id bigint not null default 1");
        assertThat(sql).contains("status varchar(32) not null default 'draft'");
        assertThat(sql).contains("created_by varchar(64) not null");
        assertThat(sql).contains("updated_by varchar(64) not null");
        assertThat(sql).contains("sale_price_cent bigint not null");
        assertThat(sql).contains("idx_pms_product_shop_id_id");
        assertThat(sql).contains("idx_pms_product_shop_status");
        assertThat(sql).contains("uk_pms_sku_shop_code");
        assertThat(sql).contains("idx_pms_sku_shop_product");
        assertThat(sql).contains("foreign key (product_id) references pms_product (id)");
    }
}
