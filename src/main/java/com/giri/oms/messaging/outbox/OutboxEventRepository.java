package com.giri.oms.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    boolean existsByStatus(OutboxEventStatus status);

    /**
     * Backs the {@code outbox.events.pending} gauge in {@link OutboxMetrics}.
     * Same shape as the {@code status = 'PENDING'} query the KEDA
     * ScaledObject runs directly against Postgres (see
     * k8s/07-scaledobject-worker.yaml) — this is the in-app,
     * Prometheus-visible view of the same number.
     */
    long countByStatus(OutboxEventStatus status);

    /**
     * Claims up to {@code limit} PENDING rows for this instance to publish,
     * oldest first. This is what makes running more than one instance of this
     * app safe: {@code FOR UPDATE} takes a row lock on every row selected, and
     * {@code SKIP LOCKED} means a concurrent caller (another instance's own
     * poll, running this same query at the same moment) doesn't block waiting
     * for those locks — it just moves on and locks whatever PENDING rows are
     * still free instead. Two instances polling at the same moment therefore
     * claim two disjoint sets of rows rather than both fetching, and both
     * publishing, the same events.
     *
     * <p>The lock is only useful for as long as the transaction that acquired
     * it stays open — see OutboxPublisher.publishPendingEvents, which wraps
     * the whole batch (fetch + Kafka sends + status updates) in one
     * {@code @Transactional} method for exactly this reason. A caller that
     * fetched via this method and then let the transaction commit (or never
     * had one) before actually processing the rows would get no protection
     * from it at all.
     *
     * <p>Native query because neither Spring Data's derived-query naming nor
     * its {@code @Lock} annotation has a way to express {@code SKIP LOCKED} —
     * it isn't part of standard JPA, only certain dialects (Postgres among
     * them) support it.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findAndLockPendingBatch(@Param("limit") int limit);
}
