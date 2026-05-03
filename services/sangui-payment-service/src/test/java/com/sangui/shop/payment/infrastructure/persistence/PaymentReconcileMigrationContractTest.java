package com.sangui.shop.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentReconcileMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V3__add_payment_reconcile_lookup_index.sql";

    @Test
    void paymentReconcileMigrationAddsCreatedPaymentLookupIndex() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();
        assertThat(migration.toString()).contains(MIGRATION_RESOURCE);

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("idx_pay_payment_order_shop_status_created");
        assertThat(sql).contains("shop_id, status, created_at");
    }
}
