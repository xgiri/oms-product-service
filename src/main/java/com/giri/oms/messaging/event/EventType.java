package com.giri.oms.messaging.event;

/**
 * Trimmed to just this service's own event types, unlike oms-main's EventType
 * which lists every module's events — product-service has no business
 * reason to know ORDER_CREATED, PAYMENT_CONFIRMED, etc. exist at all.
 */
public final class EventType {

    public static final String PRODUCT_CREATED = "ProductCreated";
    public static final String PRODUCT_UPDATED = "ProductUpdated";
    public static final String PRODUCT_DELETED = "ProductDeleted";

    private EventType() {
    }
}
