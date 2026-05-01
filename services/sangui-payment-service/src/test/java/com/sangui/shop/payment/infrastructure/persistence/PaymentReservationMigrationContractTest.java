package com.sangui.shop.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentReservationMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V2__add_payment_reservation_reference.sql";

    @Test
    void paymentReservationMigrationAddsReservationColumn() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();
        assertThat(migration.toString()).contains(MIGRATION_RESOURCE);

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("alter table pay_payment_order");
        assertThat(sql).contains("add column reservation_no varchar(64) not null");
    }
}
