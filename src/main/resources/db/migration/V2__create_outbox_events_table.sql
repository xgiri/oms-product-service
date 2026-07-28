-- This service's OWN local outbox table — not a connection to oms-main's
-- oms_messaging.outbox_events. Per the note left in oms-main's V20 migration:
-- the outbox pattern's correctness guarantee is "the business row and its
-- outbox row commit in the same transaction, against the same database" — a
-- shared table only works while everything's in one database. Now that
-- Product has its own database, it needs its own local outbox again, or a
-- ProductServiceImpl transaction writing to a "products" row here and an
-- "outbox_events" row somewhere else stops being atomic.
--
-- Shape matches oms-main's outbox_events exactly (including the
-- correlation_id column added there via V15) so OutboxEvent/OutboxService/
-- OutboxPublisher/OutboxEventRepository could be carried over unchanged.

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    partition_key   VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    correlation_id  VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP(6) NOT NULL,
    published_at    TIMESTAMP(6),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
