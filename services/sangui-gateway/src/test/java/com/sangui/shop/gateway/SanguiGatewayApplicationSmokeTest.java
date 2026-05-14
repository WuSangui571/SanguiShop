package com.sangui.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "sangui.security.jwt.secret=test-secret-with-enough-entropy"
        }
)
class SanguiGatewayApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void startupClassExists() {
        assertThat(SanguiGatewayApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void adminSeckillRouteIsConfigured() {
        RouteDefinition route = gatewayProperties.getRoutes().stream()
                .filter(candidate -> "sangui-seckill-admin".equals(candidate.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(route.getUri()).isEqualTo(URI.create("lb://sangui-seckill"));
        assertThat(route.getPredicates())
                .extracting(PredicateDefinition::toString)
                .anySatisfy(predicate -> assertThat(predicate).contains("Path", "/api/admin/seckill/**"));
    }
}
