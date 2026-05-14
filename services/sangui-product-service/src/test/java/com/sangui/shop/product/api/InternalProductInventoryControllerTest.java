package com.sangui.shop.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.product.application.ProductInventoryService;
import com.sangui.shop.product.domain.ProductInventoryReservationRecord;
import com.sangui.shop.product.domain.ProductInventoryReservationSnapshot;
import com.sangui.shop.product.domain.ProductInventoryReservationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = InternalProductInventoryController.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@Import(GlobalApiExceptionHandler.class)
class InternalProductInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductInventoryService productInventoryService;

    @Test
    void reserveReturnsStableEnvelope() throws Exception {
        org.mockito.Mockito.when(productInventoryService.reserve(any(), any()))
                .thenReturn(new ProductInventoryReservationSnapshot(
                        1L,
                        "ord:10001:req-001",
                        ProductInventoryReservationStatus.RESERVED,
                        List.of(new ProductInventoryReservationRecord(
                                1L,
                                "ord:10001:req-001",
                                301L,
                                401L,
                                "shoe-42",
                                "Sneaker 42",
                                59900L,
                                2,
                                ProductInventoryReservationStatus.RESERVED,
                                "trace-product-inventory"
                        ))
                ));

        mockMvc.perform(post("/internal/products/inventory/reservations")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "reservationNo", "ord:10001:req-001",
                                "items", List.of(Map.of(
                                        "skuId", 401,
                                        "quantity", 2
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_INVENTORY_RESERVED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-inventory"))
                .andExpect(jsonPath("$.data.reservationNo").value("ord:10001:req-001"))
                .andExpect(jsonPath("$.data.status").value("reserved"));
    }
}
