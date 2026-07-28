package com.giri.oms.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer instrumentation for the transactional outbox — the JVM /
 * HTTP / Kafka-client metrics elsewhere come for free from Spring Boot's
 * actuator + Kafka autoconfiguration once a {@link MeterRegistry} bean
 * exists, but outbox depth and publish outcomes are domain-specific and
 * need registering explicitly.
 *
 * <p>Deliberately not gated by {@code app.process.role} the way
 * {@link OutboxPublisher} is: {@code outbox.events.pending} is a plain count
 * against the shared {@code outbox_events} table, true regardless of which
 * instance asks, so it's just as meaningful scraped from a {@code web}
 * instance as a {@code worker} one. Only {@link #recordPublished} /
 * {@link #recordFailed} are worker-only in practice, simply because nothing
 * else ever calls them.
 *
 * <p>{@code outbox.events.pending} is the in-app, Prometheus-queryable twin
 * of the {@code SELECT COUNT(*) ... WHERE status = 'PENDING'} query the KEDA
 * {@code ScaledObject} runs directly against Postgres (see
 * {@code k8s/07-scaledobject-worker.yaml}) — same number, different consumer.
 * KEDA needs it outside the app (to decide replica count before any replica
 * exists to scrape); this is for dashboards/alerts once instances are up.
 */
@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Timer publishTimer;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;

        meterRegistry.gauge(
                "outbox.events.pending",
                Tags.empty(),
                outboxEventRepository,
                repo -> repo.countByStatus(OutboxEventStatus.PENDING));

        this.publishedCounter = Counter.builder("outbox.events.published")
                .description("Outbox events successfully published to Kafka")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("outbox.events.failed")
                .description("Outbox events that failed to publish (will be retried on the next poll)")
                .register(meterRegistry);

        this.publishTimer = Timer.builder("outbox.events.publish.duration")
                .description("Time to publish a single outbox event to Kafka, including the send() get() wait")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /** Call with the elapsed duration of a successful {@code doPublish}. */
    public void recordPublished(long durationNanos) {
        publishedCounter.increment();
        publishTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Call for any failed publish attempt. Not tagged by failure reason —
     * {@code ex.getMessage()} is unbounded/high-cardinality (broker error
     * text, timeout details) and a Prometheus label with that shape would be
     * a metrics-cardinality problem, not a useful dimension. The
     * {@code last_error} column on the row itself (see OutboxEvent) already
     * carries that detail for anyone debugging a specific event.
     */
    public void recordFailed() {
        failedCounter.increment();
    }
}
