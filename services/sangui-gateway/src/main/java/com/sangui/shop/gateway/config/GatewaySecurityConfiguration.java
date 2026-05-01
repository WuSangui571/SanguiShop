package com.sangui.shop.gateway.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }
}
