package com.giri.oms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public class BaseEntity {

    // KNOWN GAP (deferred): unlike OutboxEvent.createdAt/publishedAt, these two
    // still come from Hibernate's own internal clock via @CreationTimestamp/
    // @UpdateTimestamp, not the app's injected Clock bean (see ClockConfig).
    // That makes them unswappable in tests. Fixing this properly needs a
    // custom Hibernate generator backed by a statically-held Clock (Hibernate
    // instantiates generators via reflection, not Spring DI, so the bean can't
    // be constructor-injected directly) — bigger blast radius than OutboxEvent
    // since every BaseEntity subclass (Customer, AppUser, Shipment, Order,
    // OrderItem, Payment, Inventory, InventoryReservation, Product) is affected.
    // Left as-is for now; revisit in a dedicated pass.
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Optimistic locking: Hibernate includes "AND version = ?" on every UPDATE and
    // increments it on success. If another transaction already updated the row
    // (and bumped the version) since this entity was loaded, the UPDATE affects
    // zero rows and Hibernate throws ObjectOptimisticLockingFailureException —
    // caught centrally in GlobalExceptionHandler and surfaced as a 409, instead of
    // one write silently overwriting another with no error to either caller.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}