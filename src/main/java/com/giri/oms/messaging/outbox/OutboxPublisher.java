package com.giri.oms.messaging.outbox;

import com.giri.oms.common.correlation.MdcCorrelation;
import com.giri.oms.messaging.config.KafkaAppProperties;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Guarded by app.process.role (see application.properties) — only active on
 * a "worker" process, or on any process at all if the property is unset. Set
 * app.process.role=web on an instance to keep it API-only; that instance's
 * OutboxService.enqueue calls still write PENDING rows, some other "worker"
 * instance just has to be running somewhere to actually flush them.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.process.role", havingValue = "worker", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;
    private final OutboxMetrics outboxMetrics;
    private final Tracer tracer;

    /**
     * {@code @Transactional} here isn't incidental — it's what makes
     * {@link OutboxEventRepository#findAndLockPendingBatch} actually do
     * anything. That query's {@code FOR UPDATE SKIP LOCKED} only protects
     * these rows from a concurrent poller for as long as this transaction
     * stays open, so the fetch, every Kafka send, and every status update
     * below all have to happen inside the one transaction this method opens
     * — commit only happens once the whole batch is done, which is the
     * point at which the row locks are actually released and another
     * instance's poll can see these rows again (now PUBLISHED/FAILED, not
     * PENDING, so it won't try to touch them again anyway).
     *
     * <p>Trade-off worth knowing about: this means a slow batch (e.g. Kafka
     * broker latency pushing several sends close to SEND_TIMEOUT_SECONDS)
     * holds those row locks, and this DB transaction, open for the whole
     * batch's duration — other instances aren't blocked by it (they'll just
     * SKIP LOCKED past these rows and claim whatever's left), but it does
     * mean a slow batch delays when the connection and locks free up. If
     * outbox throughput ever needs tuning, batch-size and
     * SEND_TIMEOUT_SECONDS are the two knobs that trade off "lock/connection
     * held per poll" against "events published per poll".
     */
    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms}")
    @Transactional
    public void publishPendingEvents() {
        int batchSize = kafkaAppProperties.outbox().batchSize();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findAndLockPendingBatch(batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending outbox event(s)", pendingEvents.size());
        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    public void publishSingleEvent(OutboxEvent event) {
        // This runs on scheduling-1, a shared pool thread with no MDC or live
        // span of its own — scope both the correlation id and a span linked
        // back to the enqueueing trace (captured back when
        // OutboxService.enqueue() wrote this row) to just this event's
        // publish, so a batch of unrelated events flushed in the same poll
        // never bleed into each other's log lines or traces. See
        // OutboxTraceLinking for why a link rather than a parent/child span.
        // Ported from oms-main's identical OutboxPublisher — see this repo's
        // README for why this was a separate pass from the original Stage 2
        // tracing retrofit.
        MdcCorrelation.runWithCorrelationId(event.getCorrelationId(), () ->
                OutboxTraceLinking.runWithLinkedSpan(tracer, event.getTraceId(), event.getSpanId(), () -> doPublish(event)));
    }

    private void doPublish(OutboxEvent event) {
        long startNanos = System.nanoTime();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getTopic(), null, event.getPartitionKey(), event.getPayload());
            record.headers().add(new RecordHeader("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8)));
            if (event.getCorrelationId() != null) {
                record.headers().add(new RecordHeader("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
            }

            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            event.markPublished(clock);
            outboxEventRepository.save(event);
            outboxMetrics.recordPublished(System.nanoTime() - startNanos);
            log.info("Published outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.recordFailure("Interrupted while publishing to Kafka");
            outboxEventRepository.save(event);
            outboxMetrics.recordFailed();
            log.warn("Interrupted while publishing outbox event id={}", event.getId());
        } catch (ExecutionException | TimeoutException ex) {
            event.recordFailure(ex.getMessage());
            outboxEventRepository.save(event);
            outboxMetrics.recordFailed();
            log.warn("Failed to publish outbox event id={} type={}: {}", event.getId(), event.getEventType(), ex.getMessage());
        }
    }
}
