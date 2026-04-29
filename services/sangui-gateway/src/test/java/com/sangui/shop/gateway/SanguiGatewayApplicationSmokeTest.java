package com.sangui.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiGatewayApplication.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
class SanguiGatewayApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startupClassExists() {
        assertThat(SanguiGatewayApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
