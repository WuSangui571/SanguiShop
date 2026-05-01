package com.sangui.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.OrderNumberGenerator;
import com.sangui.shop.order.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiOrderApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
class SanguiOrderApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private ProductCatalogClient productCatalogClient;

    @MockBean
    private OrderNumberGenerator orderNumberGenerator;

    @Test
    void startupClassExists() {
        assertThat(SanguiOrderApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
