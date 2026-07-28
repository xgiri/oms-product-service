package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the product module (see ProductServiceImpl.deleteProduct)
 * whenever a product transitions ACTIVE -> DISCONTINUED (Phase 1 step 2 of
 * the microservices-prep plan converted this from a hard delete to a status
 * flip — see Product.status). Not published again on a repeat delete call
 * against an already-discontinued product; deleteProduct treats that as an
 * idempotent no-op.
 *
 * Consumed by the inventory module's ProductEventInventoryConsumer (Phase 1
 * step 3) — deliberately as a no-op for the product_ref replica itself: an
 * existing inventory row can still reference a discontinued product, and it
 * still needs a name to display, so the replica keeps the last known name
 * rather than clearing it. See that consumer's Javadoc for the full
 * reasoning.
 */
public record ProductDeletedEvent(
        UUID eventId,
        Long productId,
        LocalDateTime occurredAt
) {
}
