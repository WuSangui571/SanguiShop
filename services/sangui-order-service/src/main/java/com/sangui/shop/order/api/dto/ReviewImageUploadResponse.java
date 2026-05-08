package com.sangui.shop.order.api.dto;

public record ReviewImageUploadResponse(
        String url,
        String contentType,
        long sizeBytes
) {
}
