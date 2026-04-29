package com.sangui.shop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiUserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "sangui.security.jwt.secret=test-secret-with-enough-entropy",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
class SanguiUserApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private UserRepository userRepository;

    @Test
    void startupClassExists() {
        assertThat(SanguiUserApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
