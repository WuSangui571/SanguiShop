package com.sangui.shop.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.product.domain.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiProductApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
class SanguiProductApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ProductRepository productRepository;

    @Test
    void startupClassExists() {
        assertThat(SanguiProductApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
