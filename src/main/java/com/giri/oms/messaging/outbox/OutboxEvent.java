package com.giri.oms.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * The correlation id (see CorrelationIdConstants.MDC_KEY) that was in MDC
     * on whichever thread enqueued this event — the original HTTP request
     * thread for OrderCreated, or a KafkaListenerEndpointContainer thread for
     * everything downstream (see MdcCorrelation). Null if nothing was in MDC
     * at enqueue time. OutboxPublisher carries this onto the Kafka record as
     * a header so the next consumer can pick it back up.
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    // Set explicitly in pending() from the injected Clock, rather than via
    // Hibernate's @CreationTimestamp — that annotation reads its own internal
    // clock (not this app's Clock bean), which made this field unswappable in
    // tests. This keeps every timestamp on this entity flowing through the
    // same clock.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static OutboxEvent pending(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            String correlationId,
            Clock clock) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.topic = topic;
        event.partitionKey = partitionKey;
        event.payload = payload;
        event.correlationId = correlationId;
        event.status = OutboxEventStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = LocalDateTime.now(clock);
        return event;
    }

    public void markPublished(Clock clock) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now(clock);
        this.lastError = null;
    }

    public void recordFailure(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
    }
}
