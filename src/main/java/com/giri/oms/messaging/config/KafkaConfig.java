package com.giri.oms.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Stage 1: product-service is producer-only — it publishes onto
 * oms.product.events (consumed by the monolith's inventory module, see
 * ProductEventInventoryConsumer there) but has no @KafkaListener of its own
 * yet. That's why, unlike oms-main's KafkaConfig, there's no
 * DefaultErrorHandler/DeadLetterPublishingRecoverer bean here — that machinery
 * only matters once something in this service actually consumes a topic.
 * The DLT topic below is still declared now so it exists in Kafka ahead of
 * whenever a consumer (and its error handler) is added.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic productEventsTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().productEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic productEventsDeadLetterTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().productEvents() + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
