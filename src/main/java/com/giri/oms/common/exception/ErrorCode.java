package com.giri.oms.common.exception;

import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.Set;

/**
 * Trimmed from oms-main's ErrorCode: only the common/platform (CM) codes and
 * Product's own (PR) codes are here — CU/OR/PY/SH/IN/AU all belonged to
 * modules that don't exist in this codebase anymore. Values/wire format for
 * every code kept below are UNCHANGED from oms-main (PRODUCT_NOT_FOUND is
 * still "EPR100") — this is a client-facing contract, so a client already
 * handling that code needs no change post-extraction.
 * <p>
 * See oms-main's ErrorCode for the full format/stability-contract Javadoc —
 * not repeated here to avoid drift between two copies of the same prose;
 * the contract itself (append-only, code:meaning is permanent) still applies.
 */
public enum ErrorCode {

    // ---- Common / platform (CM) ----
    VALIDATION_FAILED("E", "CM", "001", HttpStatus.BAD_REQUEST,
            "One or more fields failed validation"),
    INVALID_SORT_FIELD("E", "CM", "002", HttpStatus.BAD_REQUEST,
            "Invalid sort field: %s"),
    UNAUTHENTICATED("E", "CM", "003", HttpStatus.UNAUTHORIZED,
            "A valid Bearer token is required to access this resource"),
    ACCESS_DENIED("E", "CM", "101", HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action"),
    OPTIMISTIC_LOCK_CONFLICT("E", "CM", "103", HttpStatus.CONFLICT,
            "This record was modified by someone else in the meantime — please refresh and try again."),
    RESOURCE_CONFLICT("E", "CM", "105", HttpStatus.CONFLICT,
            "This request conflicts with an existing record — please check for a duplicate and try again."),
    INTERNAL_ERROR("E", "CM", "500", HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."),

    // ---- Product (PR) ----
    PRODUCT_NOT_FOUND("E", "PR", "100", HttpStatus.NOT_FOUND,
            "Product not found with id: %d");

    private final String prefix;
    private final String componentId;
    private final String errorId;
    private final HttpStatus httpStatus;
    private final String messageTemplate;

    ErrorCode(String prefix, String componentId, String errorId, HttpStatus httpStatus, String messageTemplate) {
        this.prefix = prefix;
        this.componentId = componentId;
        this.errorId = errorId;
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    public String code() {
        return prefix + componentId + errorId;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String formatMessage(Object... args) {
        return String.format(messageTemplate, args);
    }

    public String sampleMessage() {
        return messageTemplate
                .replace("%d", "123")
                .replace("%s", "example");
    }

    static {
        Set<String> seen = new HashSet<>();
        for (ErrorCode value : values()) {
            if (!seen.add(value.code())) {
                throw new ExceptionInInitializerError(
                        "Duplicate ErrorCode.code() value: " + value.code() + " (on " + value.name() + ")");
            }
        }
    }
}
