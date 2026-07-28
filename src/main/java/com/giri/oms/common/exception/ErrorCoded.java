package com.giri.oms.common.exception;

/**
 * Implemented by domain exceptions that carry a stable {@link ErrorCode} for clients to
 * branch on. GlobalExceptionHandler reads {@link #getErrorCode()} instead of hardcoding
 * the mapping itself, so adding a new domain exception only requires implementing this
 * interface — no change needed in the handler.
 */
public interface ErrorCoded {

    ErrorCode getErrorCode();
}
