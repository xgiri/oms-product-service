package com.giri.oms.messaging.outbox;

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Collections;

/**
 * Ported from oms-main's identical class (own copy, not shared — same
 * reasoning as this service's own local outbox in the first place; see this
 * repo's README for why this was a separate pass from the original Stage 2
 * tracing retrofit). Tracing counterpart to correlation-id propagation.
 *
 * <p>OutboxPublisher runs on scheduling-1, a thread with no live span of its
 * own by the time it picks a row up — Kafka's observation instrumentation
 * would otherwise start a brand new, disconnected trace for every publish.
 * That's fine for the "what did this poll do" question, but it breaks the
 * "why did this ProductCreated event take 4 seconds to reach Inventory"
 * question, since the publish trace would have no relationship back to the
 * request that enqueued it.
 *
 * <p>A parent/child span can't fix this — the original request span is long
 * finished by the time the poller runs, possibly seconds later. OpenTelemetry
 * span links exist for exactly this async/batched case: "this span is
 * related to that earlier trace" without claiming to be its direct child.
 * This wraps the publish in a short-lived span carrying that link, so
 * Kafka's own send span (created inside the wrapped action) nests under it
 * as a child, and that whole subtree is traceable back to the original
 * request in Tempo.
 */
final class OutboxTraceLinking {

    private OutboxTraceLinking() {
    }

    static void runWithLinkedSpan(Tracer tracer, String traceId, String spanId, Runnable action) {
        if (traceId == null || traceId.isBlank() || spanId == null || spanId.isBlank()) {
            action.run();
            return;
        }

        TraceContext linkedContext = tracer.traceContextBuilder()
                .traceId(traceId)
                .spanId(spanId)
                .sampled(true)
                .build();

        Span publishSpan = tracer.spanBuilder()
                .name("outbox-publish")
                .addLink(new Link(linkedContext, Collections.emptyMap()))
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(publishSpan)) {
            action.run();
        } finally {
            publishSpan.end();
        }
    }
}
