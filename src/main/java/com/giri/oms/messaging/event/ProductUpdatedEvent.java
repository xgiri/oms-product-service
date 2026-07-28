package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the product module (see ProductServiceImpl.updateProduct).
 * Carries the full current name/price rather than a diff — simplest thing
 * that works for a replica consumer to upsert against, and consistent with
 * how OrderCreatedEvent snapshots full values rather than deltas.
 */
public record ProductUpdatedEvent(
        UUID eventId,
        Long productId,
        String name,
        BigDecimal price,
        LocalDateTime occurredAt
) {
}
