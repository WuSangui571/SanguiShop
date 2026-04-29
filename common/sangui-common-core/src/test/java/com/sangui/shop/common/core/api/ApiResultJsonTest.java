package com.sangui.shop.common.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResultJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void serializesResponseEnvelopeFields() throws Exception {
        ApiResult<Map<String, Object>> result = ApiResult.ok(
                "OK",
                Map.of("shopId", 1L),
                "trace-001"
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("code", "message", "data", "traceId", "timestamp");
        assertThat(json.get("code").asText()).isEqualTo("OK");
        assertThat(json.get("message").asText()).isEqualTo("ok");
        assertThat(json.get("data").get("shopId").asLong()).isEqualTo(1L);
        assertThat(json.get("traceId").asText()).isEqualTo("trace-001");
        assertThat(json.get("timestamp").asText()).isNotBlank();
    }
}
