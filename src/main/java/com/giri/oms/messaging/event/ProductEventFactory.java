package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builds Product's three lifecycle events and their shared outbox routing
 * metadata. Bundled into one factory rather than three (unlike
 * OrderCreatedEventFactory/OrderConfirmedEventFactory/OrderCancelledEventFactory,
 * which are one class each) because all three Product events share the same
 * aggregate type, topic, and id/partition-key derivation — three near-empty
 * classes would just repeat that metadata three times for no benefit.
 */
@Component
public class ProductEventFactory {

    private static final String PRODUCT_AGGREGATE_TYPE = "Product";

    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;

    public ProductEventFactory(KafkaAppProperties kafkaAppProperties, Clock clock) {
        this.kafkaAppProperties = kafkaAppProperties;
        this.clock = clock;
    }

    public ProductCreatedEvent created(Long productId, String name, BigDecimal price, UUID eventId) {
        return new ProductCreatedEvent(eventId, productId, name, price, LocalDateTime.now(clock));
    }

    public ProductUpdatedEvent updated(Long productId, String name, BigDecimal price, UUID eventId) {
        return new ProductUpdatedEvent(eventId, productId, name, price, LocalDateTime.now(clock));
    }

    public ProductDeletedEvent deleted(Long productId, UUID eventId) {
        return new ProductDeletedEvent(eventId, productId, LocalDateTime.now(clock));
    }

    public String aggregateType() {
        return PRODUCT_AGGREGATE_TYPE;
    }

    public String aggregateId(Long productId) {
        return productId.toString();
    }

    public String partitionKey(Long productId) {
        return productId.toString();
    }

    public String topic() {
        return kafkaAppProperties.topics().productEvents();
    }
}
