package com.sangui.shop.seckill;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
        classes = SanguiSeckillApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
class SanguiSeckillApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ActivityRepository activityRepository;

    @MockBean
    private ProductSkuSnapshotClient productSkuSnapshotClient;

    @Test
    void startupClassExists() {
        assertThat(SanguiSeckillApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
