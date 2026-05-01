package com.sangui.shop.order.infrastructure.ordernumber;

import com.sangui.shop.order.domain.OrderNumberGenerator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidOrderNumberGenerator implements OrderNumberGenerator {

    @Override
    public String nextOrderNo() {
        String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return "ORD" + raw.substring(0, 20);
    }
}
