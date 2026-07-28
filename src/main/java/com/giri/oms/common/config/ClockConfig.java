package com.giri.oms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system {@link Clock} as a bean so code depends on {@code Clock}
 * instead of calling {@code LocalDateTime.now()} directly. This makes
 * timestamp-producing code deterministic in tests: swap in
 * {@code Clock.fixed(...)} via {@code @MockBean}/a test {@code @Bean} instead
 * of relying on wall-clock time.
 * <p>
 * Uses the system default time zone to match the previous {@code
 * LocalDateTime.now()} behavior exactly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
