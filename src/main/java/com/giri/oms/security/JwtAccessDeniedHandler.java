package com.giri.oms.security;

import com.giri.oms.common.exception.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Invoked when an authenticated user is correctly identified but lacks the
 * role/authority a resource requires (e.g. a non-admin hitting a DELETE
 * endpoint guarded by @PreAuthorize). Distinct from JwtAuthenticationEntryPoint,
 * which handles requests with no valid authentication at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper objectMapper;
    private final Clock clock;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied — path: {}, reason: {}", request.getRequestURI(), accessDeniedException.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(clock),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                com.giri.oms.common.exception.ErrorCode.ACCESS_DENIED.code(),
                com.giri.oms.common.exception.ErrorCode.ACCESS_DENIED.formatMessage(),
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
