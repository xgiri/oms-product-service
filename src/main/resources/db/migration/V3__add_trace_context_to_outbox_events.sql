-- Ported from oms-main's own V21__add_trace_context_to_outbox_events.sql —
-- see this repo's messaging.outbox.OutboxEvent / OutboxTraceLinking for the
-- full reasoning. Bridges the outbox pattern's trace gap: OutboxService's
-- enqueue() runs on whatever thread called it (an HTTP request thread) with
-- a live tracing span in context, but OutboxPublisher's @Scheduled poller
-- picks the row up later on scheduling-1, a thread with no span of its own.
-- Left alone, every event published from here produces a trace disconnected
-- from whatever request caused it.
--
-- These columns carry the W3C trace id (32 hex chars) and span id (16 hex
-- chars) that were current at enqueue time, the same way correlation_id
-- already carries the MDC correlation id across that same thread hop.
-- Nullable for the same reason correlation_id is: no live span at enqueue
-- time just means the eventual publish gets no link, not an error.
ALTER TABLE outbox_events ADD COLUMN trace_id VARCHAR(32);
ALTER TABLE outbox_events ADD COLUMN span_id VARCHAR(16);
