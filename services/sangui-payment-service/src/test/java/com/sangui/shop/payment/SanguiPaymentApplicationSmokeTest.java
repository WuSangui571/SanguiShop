package com.sangui.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.payment.client.OrderPaymentClient;
import com.sangui.shop.payment.domain.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiPaymentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
class SanguiPaymentApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private OrderPaymentClient orderPaymentClient;

    @Test
    void startupClassExists() {
        assertThat(SanguiPaymentApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
