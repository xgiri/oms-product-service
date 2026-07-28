package com.giri.oms.product.constants;

/**
 * Centralized message strings for the Product module — exception messages,
 * validation messages, and (later) log/response messages all live here
 * instead of being hardcoded inline where they're used.
 */
public final class ProductConstants {

    private ProductConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    // PRODUCT_NOT_FOUND message now lives in com.giri.oms.common.exception.ErrorCode
    // alongside its error code.
    public static final String PRODUCT_ALREADY_EXISTS_MESSAGE = "Product already exists with name: %s";

    // ---- Bean Validation messages ----
    public static final String NAME_REQUIRED_MESSAGE = "Product name must not be blank";
    public static final String PRICE_REQUIRED_MESSAGE = "Product price must not be null";
    public static final String PRICE_POSITIVE_MESSAGE = "Product price must be greater than zero";
    public static final String PRICE_DIGITS_MESSAGE = "Price must have up to 5 integer digits and 2 decimals";

    // ---- Success / log messages ----
    public static final String PRODUCT_CREATED_LOG = "Product created successfully with id: {}";
    public static final String PRODUCT_UPDATED_LOG = "Product updated successfully with id: {}";
    public static final String PRODUCT_DELETED_LOG = "Product deleted successfully with id: {}";
}