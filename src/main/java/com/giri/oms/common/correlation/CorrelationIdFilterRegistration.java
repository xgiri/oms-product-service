package com.giri.oms.common.correlation;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class CorrelationIdFilterRegistration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistrationBean() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.addUrlPatterns("/*");
        // Earliest possible order — Spring Security's own filter chain registers
        // itself at order -100 (see SecurityProperties.DEFAULT_FILTER_ORDER), and
        // LoginRateLimitFilter sits at order 1. This needs to run before both, so
        // that a rejected-login or rate-limited log line still carries an ID.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
