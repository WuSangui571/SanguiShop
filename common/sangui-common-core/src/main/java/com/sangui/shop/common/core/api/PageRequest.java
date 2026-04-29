package com.sangui.shop.common.core.api;

public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
    }
}
