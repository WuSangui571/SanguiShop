package com.sangui.shop.payment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentCompensationQueryResponseJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void serializesAggregateHistoryQueryResponseFields() throws Exception {
        PaymentCompensationRecordResponse payment = new PaymentCompensationRecordResponse(
                201L,
                "PAY-001",
                101L,
                "ORD-001",
                "10001",
                "mock",
                "failed",
                59900L,
                "trace-payment",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "order confirm timeout",
                "trace-history",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00")
        );
        PaymentCompensationAttemptResponse attempt = new PaymentCompensationAttemptResponse(
                1L,
                201L,
                101L,
                "PAY-001",
                "ORD-001",
                "ord:10001:req-001",
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "order confirm timeout",
                "trace-history",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:05:00+08:00")
        );
        PaymentCompensationQueryResponse response = new PaymentCompensationQueryResponse(
                1L,
                1,
                20,
                1L,
                List.of(new PaymentCompensationAggregateResponse(
                        payment,
                        1L,
                        1L,
                        OffsetDateTime.parse("2026-05-03T12:05:00+08:00"),
                        List.of(attempt)
                ))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("shopId", "pageNo", "pageSize", "total", "items");
        assertThat(json.get("items").get(0).fieldNames()).toIterable()
                .containsExactly("payment", "matchedAttemptCount", "totalAttemptCount", "latestAttemptAt", "attempts");
        assertThat(json.get("items").get(0).get("payment").get("paymentNo").asText()).isEqualTo("PAY-001");
        assertThat(json.get("items").get(0).get("attempts").get(0).get("trigger").asText()).isEqualTo("scheduler");
    }
}
