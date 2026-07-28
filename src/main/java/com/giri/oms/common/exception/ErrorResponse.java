package com.giri.oms.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @param errorCode the 6-character {@code [PREFIX][COMPONENT_ID][ERROR_ID]} code for
 *                   client-side branching, e.g. {@code "EPR100"} — see {@link ErrorCode}
 *                   for the full set, the format, and the stability contract.
 */
public record ErrorResponse(
        @Schema(description = "When the error occurred", example = "2026-07-19T18:07:21.911", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime timestamp,

        @Schema(description = "HTTP status code", example = "401", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,

        @Schema(description = "HTTP status reason phrase", example = "Unauthorized", requiredMode = Schema.RequiredMode.REQUIRED)
        String error,

        @Schema(description = "Stable 6-character error code for client-side branching", example = "ECM003", requiredMode = Schema.RequiredMode.REQUIRED)
        String errorCode,

        @Schema(description = "Human-readable error message", example = "A valid Bearer token is required to access this resource", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "Request path that produced this error", example = "/api/v1/orders/999", requiredMode = Schema.RequiredMode.REQUIRED)
        String path
) {}
