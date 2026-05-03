package com.sangui.shop.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SanguiOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SanguiOrderApplication.class, args);
    }
}
