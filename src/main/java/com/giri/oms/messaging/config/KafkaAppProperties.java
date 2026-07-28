package com.giri.oms.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trimmed to just productEvents — no orderEvents topic in this service. */
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaAppProperties(Topics topics, Outbox outbox) {

    public record Topics(String productEvents) {
    }

    public record Outbox(long pollIntervalMs, int batchSize) {
    }
}
