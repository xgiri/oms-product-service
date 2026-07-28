package com.giri.oms.common.exception;

import com.giri.oms.product.exception.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trimmed from oms-main's GlobalExceptionHandler: only the handlers
 * product-service actually needs. Dropped relative to the original —
 * Customer/Order/Payment/Shipment/Inventory-specific handlers (those modules
 * don't exist here), LockAcquisitionException (no distributed locking in this
 * service), and the login-failure handler (this service never issues tokens,
 * only verifies them — see security.SecurityConfig, no login endpoint exists
 * to fail).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        log.warn("Product not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.data.core.PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortProperty(
            org.springframework.data.core.PropertyReferenceException ex, HttpServletRequest request) {
        log.warn("Invalid sort property in request — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.INVALID_SORT_FIELD,
                ErrorCode.INVALID_SORT_FIELD.formatMessage(ex.getPropertyName()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                          HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            fieldErrors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        });

        log.warn("Validation failed — path: {}, fields: {}", request.getRequestURI(), fieldErrors.keySet());

        HttpStatus status = ErrorCode.VALIDATION_FAILED.httpStatus();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now(clock));
        response.put("status", status.value());
        response.put("error", "Validation Failed");
        response.put("errorCode", ErrorCode.VALIDATION_FAILED.code());
        response.put("path", request.getRequestURI());
        response.put("errors", fieldErrors);

        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortField(InvalidSortFieldException ex, HttpServletRequest request) {
        log.warn("Invalid sort field — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(codeOf(ex), ex.getMessage(), request);
    }

    // See oms-main's GlobalExceptionHandler for why this needs to be its own
    // handler rather than falling through to the catch-all below — same
    // reasoning applies here (@PreAuthorize on ProductController.deleteProduct
    // throws this from inside the MVC dispatch, not the security filter chain).
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex,
                                                             HttpServletRequest request) {
        log.warn("Access denied — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED.formatMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict — path: {}, entity: {}", request.getRequestURI(), ex.getPersistentClassName());
        return build(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT.formatMessage(), request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.RESOURCE_CONFLICT, ErrorCode.RESOURCE_CONFLICT.formatMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception — path: {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.formatMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
        HttpStatus status = errorCode.httpStatus();
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(clock),
                status.value(),
                status.getReasonPhrase(),
                errorCode.code(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }

    private ErrorCode codeOf(ErrorCoded ex) {
        return ex.getErrorCode();
    }
}
