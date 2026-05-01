package com.sangui.shop.product.client.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ProductSkuSnapshotRequest(
        @NotNull @Positive Long shopId,
        @NotEmpty List<@NotNull @Positive Long> skuIds
) {
}
