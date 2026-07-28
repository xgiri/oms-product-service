package com.giri.oms.common.openapi;

import com.giri.oms.common.exception.ErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which domain-specific {@link ErrorCode}s a controller endpoint can return,
 * so {@link ErrorCodeOperationCustomizer} can generate the matching Swagger
 * {@code ApiResponse} entries automatically instead of someone hand-writing them.
 * <p>
 * Only list codes that are specific to <b>this</b> endpoint — e.g. {@code
 * ORDER_NOT_FOUND} on a get-by-id. Cross-cutting codes that apply to many endpoints
 * uniformly ({@code VALIDATION_FAILED} on any {@code @Valid} body, {@code
 * UNAUTHENTICATED}/{@code ACCESS_DENIED} on any {@code @PreAuthorize}'d method, {@code
 * INTERNAL_ERROR} on everything) are detected and added automatically by the
 * customizer — do not repeat them here.
 * <p>
 * Example:
 * <pre>{@code
 * @ApiErrorCodes({ErrorCode.PRODUCT_NOT_FOUND})
 * @GetMapping("{id}")
 * public ResponseEntity<ProductResponse> getProductById(...) { ... }
 * }</pre>
 * <p>
 * This annotation is documentation metadata only — it has no effect on runtime
 * behavior. The actual throwing/handling still lives in the service layer and
 * {@link com.giri.oms.common.exception.GlobalExceptionHandler}; this just tells the
 * docs generator what to expect from those, which is also what
 * {@code ErrorCodeApiDocumentationConsistencyTest} checks stays true.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
