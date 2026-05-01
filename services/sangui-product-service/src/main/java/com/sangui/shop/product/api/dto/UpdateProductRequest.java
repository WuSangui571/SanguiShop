package com.sangui.shop.product.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(
        Long shopId,
        String userId,
        @NotBlank @Size(max = 128) String productName,
        @Size(max = 2048) String productDescription,
        @Valid @NotEmpty List<UpsertProductSkuRequest> skus
) {
}
