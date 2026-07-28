package com.giri.oms.common.openapi;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the error {@code ApiResponse} entries for every endpoint directly from
 * {@link ErrorCode}, combining:
 * <ul>
 *   <li>domain-specific codes declared with {@link ApiErrorCodes} on the method, and</li>
 *   <li>cross-cutting codes detected automatically from the method's own shape —
 *       {@code VALIDATION_FAILED} if a parameter is {@code @Valid}, {@code
 *       UNAUTHENTICATED}/{@code ACCESS_DENIED} if the method or its class is
 *       {@code @PreAuthorize}'d, and {@code INTERNAL_ERROR} always.</li>
 * </ul>
 * Message text, HTTP status, and the {@code errorCode} string are all read from the
 * enum at startup — nobody types {@code "EPR100"} into an annotation or a docs page by
 * hand, so it cannot go stale the way a hand-written {@code @ApiResponse} can.
 */
@Component
public class ErrorCodeOperationCustomizer implements OperationCustomizer {

    private static final String ERROR_RESPONSE_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    private final Clock clock;

    public ErrorCodeOperationCustomizer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Set<ErrorCode> codes = new LinkedHashSet<>();

        ApiErrorCodes explicit = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (explicit != null) {
            codes.addAll(List.of(explicit.value()));
        }

        codes.add(ErrorCode.INTERNAL_ERROR);
        if (hasValidatedBody(handlerMethod)) {
            codes.add(ErrorCode.VALIDATION_FAILED);
        }
        if (isSecured(handlerMethod)) {
            codes.add(ErrorCode.UNAUTHENTICATED);
            codes.add(ErrorCode.ACCESS_DENIED);
        }

        // Group by HTTP status: several codes can share one status (e.g. a 409 from
        // either ORDER_NOT_FOUND-adjacent conflicts or ILLEGAL_ORDER_STATE), and each
        // becomes one example under that status's response rather than a duplicate entry.
        Map<HttpStatus, List<ErrorCode>> byStatus = codes.stream()
                .filter(code -> code.httpStatus() != null) // e.g. INSUFFICIENT_STOCK never reaches a controller
                .collect(Collectors.groupingBy(ErrorCode::httpStatus, LinkedHashMap::new, Collectors.toList()));

        byStatus.forEach((status, errorCodes) -> mergeResponse(operation, status, errorCodes));

        return operation;
    }

    private boolean hasValidatedBody(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(Valid.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSecured(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }

    private void mergeResponse(Operation operation, HttpStatus status, List<ErrorCode> errorCodes) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        String statusKey = String.valueOf(status.value());
        ApiResponse response = responses.get(statusKey);
        if (response == null) {
            response = new ApiResponse().description(status.getReasonPhrase());
            responses.addApiResponse(statusKey, response);
        } else if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(status.getReasonPhrase());
        }

        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType mediaType = content.get(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        if (mediaType == null) {
            mediaType = new MediaType().schema(new Schema<ErrorResponse>().$ref(ERROR_RESPONSE_SCHEMA_REF));
            content.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
        }

        for (ErrorCode code : errorCodes) {
            mediaType.addExamples(code.code(), buildExample(code, status));
        }
    }

    private Example buildExample(ErrorCode code, HttpStatus status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("timestamp", LocalDateTime.now(clock).withNano(0).toString());
        value.put("status", status.value());
        value.put("error", status.getReasonPhrase());
        value.put("errorCode", code.code());
        value.put("message", code.sampleMessage());
        value.put("path", "/api/v1/...");

        return new Example()
                .summary(code.code() + " — " + code.name())
                .value(value);
    }
}
