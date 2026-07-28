package com.giri.oms.common.correlation;

import org.slf4j.MDC;

import static com.giri.oms.common.correlation.CorrelationIdConstants.MDC_KEY;

/**
 * MDC glue for the parts of the saga that don't run on a servlet thread.
 *
 * <p>CorrelationIdFilter covers the HTTP request thread: it puts a
 * correlation id in MDC at the start of the request and clears it at the
 * end. Nothing does the equivalent for scheduling-1 (OutboxPublisher) or the
 * KafkaListenerEndpointContainer threads — both are thread-pool threads,
 * just like the servlet container's, so the same "always clear it or the
 * next message on this thread inherits a stale id" reasoning applies.
 *
 * <p>Usage: each {@code @KafkaListener} method reads the {@code correlationId}
 * Kafka header (set by OutboxPublisher from the value OutboxService captured
 * at enqueue time) and wraps its handling in this. Everything logged during
 * that call — including any nested {@code OutboxService.enqueue()} for the
 * saga's next event — picks the id up automatically, the same way it would
 * on the original HTTP thread.
 */
public final class MdcCorrelation {

    private MdcCorrelation() {
    }

    public static void runWithCorrelationId(String correlationId, Runnable action) {
        if (correlationId == null || correlationId.isBlank()) {
            action.run();
            return;
        }
        MDC.put(MDC_KEY, correlationId);
        try {
            action.run();
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
