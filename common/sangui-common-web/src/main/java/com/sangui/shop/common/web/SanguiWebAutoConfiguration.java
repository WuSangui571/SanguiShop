package com.sangui.shop.common.web;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
public class SanguiWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SanguiAuthenticationContextFilter> sanguiAuthenticationContextFilterRegistration() {
        FilterRegistrationBean<SanguiAuthenticationContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SanguiAuthenticationContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public GlobalApiExceptionHandler globalApiExceptionHandler() {
        return new GlobalApiExceptionHandler();
    }

    @Bean
    public WebMvcConfigurer sanguiPrincipalArgumentResolverConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new SanguiPrincipalArgumentResolver());
            }
        };
    }
}
